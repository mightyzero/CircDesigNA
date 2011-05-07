package DnaDesign.impl;

import static DnaDesign.AbstractPolymer.DnaDefinition.A;
import static DnaDesign.AbstractPolymer.DnaDefinition.C;
import static DnaDesign.AbstractPolymer.DnaDefinition.G;
import static DnaDesign.AbstractPolymer.DnaDefinition.T;

import java.awt.Point;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import DnaDesign.DomainSequence;
import DnaDesign.ExperimentDatabase;
import DnaDesign.NAFolding;
import DnaDesign.Config.CircDesigNAConfig;
import DnaDesign.Config.CircDesigNASystemElement;
import DnaDesign.impl.FoldingImpl_UnafoldExt.UnafoldFoldEntry;
import DnaDesign.impl.FoldingImpl_UnafoldExt.UnafoldRunner;

/**
 * Implements MFE prediction and folding score functions
 */
public class FoldingImpl extends CircDesigNASystemElement implements NAFolding{

	/**
	 * These parameters determine which folding engine is used to evaluate each subscore.
	 * SELF is an O(N^2) approximation which essentially takes the maximum weighted subsequence
	 * of one strand that is complementary to the other, where weight is assigned using the maximum
	 * neighbor model (with terminal nearest neighbors accounted for)
	 */
	private int mfeMODE = SELF;//SELF;
	private int pairPrMODE = NUPACK;
	private static final int NUPACK = 0, VIENNARNA=1, UNAFOLD=2, SELF=3;

	//private static final String absPathToHybridSSMinMod =  "\"C:\\Users\\Benjamin\\CLASSWORK\\002. UT UNDERGRADUATE GENERAL\\EllingtonLab\\AutoAmplifierDesign\\unafold\\hybrid-ss-min.exe\" --NA=DNA ";
	//private static final String absPathToHybridMinMod = "\"C:\\Users\\Benjamin\\CLASSWORK\\002. UT UNDERGRADUATE GENERAL\\EllingtonLab\\AutoAmplifierDesign\\unafold\\hybrid-min.exe\" --NA=DNA ";
	
	/**
	 * These parameters determine global sequence constraints, no solution generated by CircDesigNA
	 * will have stretches of more than 6 A/T or 4 G/Cs in a row: this prevents CircDesigNA from finding
	 * "trivial" solutions such as domains of a single base etc.
	 * 
	 * Additionally, stretches such as GGGG or CCCC are reportedly trouble for DNA synthesis (source?) 
	 */
	int rule_4g, rule_6at;
	{
		//DEFAULTS:
		rule_4g = 1; // cannot have 4 G's or 4 C's in a row
		rule_6at = 1; // cannot have 6 A/T bases in a row
	}
	
	/**
	 * Constructors, define parameters and / or a configuration.
	 */
	private ExperimentDatabase eParams;
	public FoldingImpl(CircDesigNAConfig sys){
		super(sys);
		eParams = new ExperimentalDuplexParamsImpl(sys);
	}
	public FoldingImpl(ExperimentDatabase params, CircDesigNAConfig sys){
		super(sys);
		eParams = params;
	}
	/**
	 * Convenience function ,returns base i of a domain sequence.
	 */
	private int base(DomainSequence ds, int i, int[][] domain){
		return ds.base(i,domain,Std.monomer);
	}
	
	private DomainSequence mutateSequence = new DomainSequence();

	public double assaySynthesizability(int mut_domain, List<DomainSequence> seqToSynthesize, int[][] domain, int[][] domain_markings) {
		mutateSequence.setDomains(mut_domain,null);
		//Evaluate the domain alone
		double res = 0;
		res += getSynthesizabilityScore(mutateSequence,domain,domain_markings);
		//Check all junctions
		for(DomainSequence seq : seqToSynthesize){
			if (seq.contains(mut_domain)){
				res += getSynthesizabilityScore(seq, domain,domain_markings);
			}
		}
		return res;
	}

	/**
	 * If certain rules are applied, this routine will check whether those rules invalidate
	 * the input sequence.
	 * @param domain_markings 
	 */
	private double getSynthesizabilityScore(DomainSequence seq, int[][] domain, int[][] domain_markings) {
		int k,q,base;
		int len = seq.length(domain);

		double sumResult = 0;
		
		// Code from David Zhang's DomainDesigner.
		// Search for 4g, if rule applied
		if (rule_4g == 1) {
			k = 0; // G-C counter
			for (q = 0; q < len; q++) {
				base = base(seq, q, domain);
				//Look for EITHER GGGG or CCCC. GCGC or any other variant is correctly ignored.
				if ((base == G)&&(k < 100)) 
					k++;
				else if (base == G)
					k = 1;
				else if ((base == C)&&(k > 100))
					k++;
				else if (base == C)
					k = 101;
				else
					k = 0;
				if ((k < 100)&&(k > 3)){
					//System.out.println("4G");
					seq.mark(q, -4, domain, domain_markings);
					sumResult += 1;
					k = 0;
				} else if (k > 103) { 
					seq.mark(q, -4, domain, domain_markings);
					//System.out.println("4C");
					sumResult += 1;
					k = 0;
				}
			}
		}

		// Search for 6at, if rule applied
		if (rule_6at == 1) {
			//look for 6+ in row of A/T
			k = 0; // AT counter
			for (q = 0; q < len; q++) {
				base = base(seq, q, domain);
				if ((base == A)||(base == T))
					k++;
				else
					k = 0;
				if (k > 5){
					//System.out.println("6AT");
					seq.mark(q, -6, domain, domain_markings);
					sumResult += 1;
					k = 0;
				}
			} 
			
			k = 0; // GC counter
			//Also look for 6+ in row of G/C
			for (q = 0; q < len; q++) {
				base = base(seq, q, domain);
				if ((base == G)||(base == C))
					k++;
				else
					k = 0;
				if (k > 5){
					//System.out.println("6GC");
					seq.mark(q, -6, domain, domain_markings);
					sumResult += 1;
					k = 0;
				}
			}
		}		

		return sumResult;
	}

	/**
	 * Main interaction scoring function
	 */
	public double mfeHybridDeltaG(DomainSequence seq1, DomainSequence seq2, int[][] domain, int[][] problemAreas) {
		if (mfeMODE==UNAFOLD){
			return Math.min(mfeHybridDeltaG_viaUnafold(seq1, seq2, domain, problemAreas) - (0), 0);
		} else {
			return Math.min(mfeHybridDeltaG_viaMatrix(seq1,seq2,domain,problemAreas) - (0), 0) ;
		}
	}

	/**
	 * Main self folding scoring function.
	 */
	public double mfeSSDeltaG(DomainSequence seq, int[][] domain, int[][] domain_markings){
		if (mfeMODE==UNAFOLD){
			return Math.max(foldSingleStranded_viaUnafold(seq, domain, domain_markings) - (0), 0);
		} else {
			return Math.min(foldSingleStranded_viaMatrix(seq, domain, domain_markings) - (0), 0);
		}
	}
	
	
	/**
	 * Interaction score shared memory buffers
	 */
	private float[][] sMatrix_shared;
	/**
	 * Each leaf (a pair of ints a,b) is either:
	 * a>0: a is  the length of a helix
	 * a<0: -a is the length of the left loop, -b is the length of the right loop.
	 */
	private int[][][] sdMatrix_shared;
	public void ensureSharedMatrices(int len1, int len2){
		if (!(sMatrix_shared!=null && len1 <= sMatrix_shared.length && len2 <= sMatrix_shared[0].length)){
			sMatrix_shared = new float[len1][len2];
			sdMatrix_shared = new int[2][len2][2];
		}
	}
	/**
	 * UNAFOLD extension: send a request out to unafold.
	 */
	public double mfeHybridDeltaG_viaUnafold(DomainSequence ds, DomainSequence ds2, int[][] domain, int[][] domain_markings) {
		UnafoldRunner ufr = new UnafoldRunner();

		int len = ds.length(domain);
		int len2 = ds2.length(domain);
		PrintWriter out = new PrintWriter(ufr.getArgsFile(0));
		{
			StringBuffer create = new StringBuffer();
			for(int k = 0; k < len; k++){
				create.append(Std.monomer.displayBase(base(ds, k, domain)));
			}
			out.println(">A");
			out.println(create.toString());
		}
		out.close();

		out = new PrintWriter(ufr.getArgsFile(1));
		{
			StringBuffer create = new StringBuffer();
			for(int k = 0; k < len2; k++){
				create.append(Std.monomer.displayBase(base(ds2, k, domain)));
			}
			out.println(">B");
			out.println(create.toString());
		}
		out.close();
		
		double val = 0;
		double PERFECTscore = 0;
		try {
			ufr.runHybridizedJob();
			final UnafoldFoldEntry next = ufr.getResults().iterator().next();
			val = next.mfeDG;
			val = Math.min(val,PERFECTscore);
			if (val == PERFECTscore){ //Unafold does not give structures in this case.
				return val;
			}
			//We have structure:
			for(int k = 0; k < len; k++){
				if (next.pairs[k]>0){
					ds.mark(k, domain, domain_markings);
				}
			}
			for(int k = 0; k < len2; k++){
				if (next.pairs[k+len]>0){
					ds2.mark(k, domain, domain_markings);
				}
			}
			
		} catch( Throwable e){
			e.printStackTrace();
		}
		return val;
	}
	/**
	 * Unafold extension: send a request out to unafold
	 */
	public double foldSingleStranded_viaUnafold(DomainSequence seq, int[][] domain, int[][] domain_markings) {
		UnafoldRunner ufr = new UnafoldRunner();

		int len = seq.length(domain);
		PrintWriter out = new PrintWriter(ufr.getArgsFile(0));
		{
			StringBuffer create = new StringBuffer();
			for(int k = 0; k < len; k++){
				create.append(Std.monomer.displayBase(base(seq, k, domain)));
			}
			out.println(">A");
			out.println(create.toString());
		}
		out.close();
		
		double val = 0;
		double PERFECTscore = 0;
		try {
			ufr.runSingleStrandedJob();
			final UnafoldFoldEntry next = ufr.getResults().iterator().next();
			val = next.mfeDG;
			val = Math.min(val,PERFECTscore);
			if (val == PERFECTscore){ //Unafold does not give structures in this case.
				return val;
			}
			//We have structure:
			for(int k = 0; k < len; k++){
				if (next.pairs[k]>0){
					seq.mark(k, domain, domain_markings);
				}
			}
		} catch( Throwable e){
			e.printStackTrace();
		}
		return val;
		
		/*
		StringBuffer create = new StringBuffer();
		int len = seq.length(domain);
		for(int k = 0; k < len; k++){
			create.append(Std.monomer.displayBase(base(seq, k, domain)));
		}
		String str = create.toString();
		try {
			Process p = Runtime.getRuntime().exec(absPathToHybridSSMinMod+str);
			Scanner in = new Scanner(p.getInputStream());
			double val = 0;
			double PERFECTscore = 0;
			try {
				while(in.hasNextLine()){
					val = new Double(in.nextLine());
					val = Math.min(val,PERFECTscore);
					return val;
				}
			} finally {
				if (val == PERFECTscore){ //"Infinite" Free Energy (?)
					return val;
				}
				in.nextLine(); //Read off "dg" line
				for(int k = 0; k < len; k++){
					char[] arr = in.nextLine().toCharArray();
					//System.out.println(new String(arr));
					int regions = 0;
					int z, end;
					for(z = 0; z < arr.length && regions < 4; z++){
						if (arr[z]=='\t'){
							regions++;
						}
					}
					for(end = z+1; end < arr.length; end++){
						if (arr[end]=='\t'){
							break;
						}
					}
					//System.out.println(new String(arr));
					int num = new Integer(new String(arr,z,end-z));
					//System.out.println(num);
					if (num > 0){
						seq.mark(num-1, domain, domain_markings);
					}
					//Thread.sleep(100);
				}
				in.close();
				p.waitFor();
			}
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		throw new RuntimeException();
		*/
	}

	/**
	 * fills the entire folding matrix with 0s. 
	 */
	private void foldSingleStranded_flushMatrixes(DomainSequence seq, DomainSequence seq2, int len1,int len2, int[][] domain){
		// NxN complementarities. 
		for (int i = 0; i < len1; i++) {
			for (int j = 0; j < len2; j++) {
				//int base1 = seq.base(i,domain);
				//int base2 = seq2.base(j,domain);
				flushFoldMatrices(i,j);
			}
		}		
	}
	private void flushFoldMatrices(int i, int j){
		sMatrix_shared[i][j] = 0;
		sdMatrix(sdMatrix_shared,i,j)[0] = 0;
		sdMatrix(sdMatrix_shared,i,j)[1] = 0;
	}

	/**
	 * These turn any folding request into an application of a simplistic O(N^2) maximum-weighted-subsequence
	 * finding problem. foldNA_viaMatrix implements the actual algorithm. 
	 */
	
	public double mfeNoDiagonalPairing(DomainSequence domainSequence, DomainSequence domainSequence2, int[][] domain, int[][] domain_markings){
		FoldNA_viaMatrix_Options mfeNoDiagonalPairsOpt = new FoldNA_viaMatrix_Options();
		mfeNoDiagonalPairsOpt.foldFullMatrix = true;
		mfeNoDiagonalPairsOpt.suppressDiagonalScores = true;
		//Run the generic folding algorithm under these conditions
		return foldNA_viaMatrix(domainSequence, domainSequence2, domain, domain_markings, mfeNoDiagonalPairsOpt);
	}
	
	public double mfeHybridDeltaG_viaMatrix(DomainSequence seq1, DomainSequence seq2, int[][] domain, int[][] domain_markings) {
		FoldNA_viaMatrix_Options mfeHybridDeltaG_viaMatrix_opt = new FoldNA_viaMatrix_Options();
		mfeHybridDeltaG_viaMatrix_opt.foldFullMatrix = true;
		//Run the generic folding algorithm under these conditions
		return foldNA_viaMatrix(seq1, seq2, domain, domain_markings, mfeHybridDeltaG_viaMatrix_opt);
	}
	public double foldSingleStranded_viaMatrix(DomainSequence seq, int[][] domain, int[][] domain_markings) {
		FoldNA_viaMatrix_Options foldSingleStranded_viaMatrix_opt = new FoldNA_viaMatrix_Options();
		foldSingleStranded_viaMatrix_opt.foldFullMatrix = false;
		//Run the generic folding algorithm under these conditions
		return foldNA_viaMatrix(seq, seq, domain, domain_markings, foldSingleStranded_viaMatrix_opt);
	}

	/**
	 * Shortcut, simply computes the free energy score of interaction assuming that seq and seq2
	 * form a base-for-base helix. Note that the two input domain sequences must have the same length. 
	 */
	public double helixDeltaG(DomainSequence seq, DomainSequence seq2, int[][] domain, int[][] domain_markings, int markStart, int markEnd, int jOffset) {
		int len1 = seq.length(domain);
		int len2 = seq2.length(domain);
		ensureSharedMatrices(len1,len2);
		float[][] Smatrix = sMatrix_shared; // score matrix
		int[][][] SDmatrix = sdMatrix_shared; // running total of helix size, 0 if current base didn't contribute.
		
		double best = 0;
		int bestI = -1, bestJ = -1;
		int helix = 0;
		for(int i = len1-1, j = -jOffset; i >= 0 && j < len2;i--, j++){
			if (j < 0){
				continue;
			}
			float gamma3 = (float) foldSingleStranded_calcGamma3(len1,len2,seq,seq2,domain,i,j,Smatrix,SDmatrix,true);
			Smatrix[i][j]=gamma3;
			if (Smatrix[i][j] < best){
				helix++;
				bestI = i;
				bestJ = j;
				best = Smatrix[i][j];
				if (Std.monomer.bindScore(base(seq,i,domain), base(seq2,j,domain)) < 0){
					if (i >= markStart && i < markEnd){
						seq.mark(i, domain, domain_markings);
						seq2.mark(j, domain, domain_markings);
					}
				}
			} else {
				helix = 0;
			}
		}
		return best;
	}
	public static class FoldNA_viaMatrix_Options {
		//True for hybridizations (versus self folding)
		public boolean foldFullMatrix;
		//Used for the "Self Similarity" Check.
		public boolean suppressDiagonalScores;
	}
	/**
	 * Implements an O(N^2) maximum weighted subsequence finding algorithm. The algorithm is familiar
	 * to one who has looked at the algorithm commonly used to solve the Longest Common Subsequence problem.
	 * It is inspired by the folding algorithm used in David Zhang's Domain Designer.
	 * 
	 * This algorithm applies a simple kernel to each cell to update its value, and then returns the largest value of 
	 * any cell in the entire matrix. The kernel is equivalent to considering the minimum free energy of three cases:
	 * dg3: Base i is paired with j, and 
	 * dg1: Base i is not paired with j, break the helix from (i+1,j) or just continue that bulge
	 * dg2: Base i is not paired with j, break the helix from (i,j-1) or just continue that bulge
	 * 
	 * Thus, the algorithm will find the minimum free energy structure when loop contributions are not taken into account.
	 * To prevent impossibly small loops, self-folding evaluation begins 4 spaces right of the diagonal (so the smallest
	 * hairpin considered has size 3). This algorithm does not consider structures which contain "bifurcations", that is, 
	 * multiloops are not supported. 
	 * 
	 * In defense of this algorithm, if any long helix exists in seq with seq2, it will be located. Thus, for the purposes
	 * of removing interactions from seq and seq2, this is good enough. Additionally, it appears that remedying the flaws described above
	 * requires upping the performance of this algorithm to O(N^3), which makes designing large (>6000 base) DNA origami scaffolds impractical.
	 */
	public double foldNA_viaMatrix(DomainSequence seq, DomainSequence seq2, int[][] domain, int[][] domain_markings, FoldNA_viaMatrix_Options options) {
		int len1 = seq.length(domain);
		int len2 = seq2.length(domain);
		if (options.suppressDiagonalScores){
			if (len1!=len2){
				throw new RuntimeException("Diagonal scores can only suppressed when folding strands of equal length");
			}
		}
		ensureSharedMatrices(len1,len2);
		/*
		for(int i = 0; i < len1; i++){
			for(int j = 0; j < len2; j++){
				flushFoldMatrices(i,j);
			}
		}
		*/
		//foldSingleStranded_makeCMatrix(seq,seq2,len1,len2,domain);
		float[][] Smatrix = sMatrix_shared; // score matrix
		int[][][] SDmatrix = sdMatrix_shared; // running total of helix size, 0 if current base didn't contribute.
		
		//Minimum hairpin size of 3, so distance from diagonal is 4
		int minHairpinSize = 1+3;
		
		double score = 0;
		int bestI = -1, bestJ = -1;
		//Only used in the single stranded version
		//Calculate looping bounds.
		for(int i = len1-1; i >= 0; i--){
			int j;
			if (options.foldFullMatrix){
				j = 0;
			} else {
				//assertion for selffolding
				if (len1!=len2){throw new RuntimeException();};
				//warning! relies on value of minhairpinsize
				j = i+minHairpinSize;
				for(int o = i; o < j && o < len2; o++){
					flushFoldMatrices(i,o);
				}
			}
			for(; j < len2; j++){
				//Left loop (m), + no bonus
				float gamma1 = (float) foldSingleStranded_calcGamma1(i,j,len1,Smatrix,SDmatrix);
				//Right loop (n), + no bonus
				float gamma2 = (float) foldSingleStranded_calcGamma2(i,j,Smatrix,SDmatrix);
				//Helix, + dummy score if new helix, - dummy score if 2nd base, + nn score is length >= 2.
				//If beginning new helix, have to add the score of the last loop.
				boolean computeHelixScore = true;
				if (options.suppressDiagonalScores){
					if (i==len2-1-j){
						computeHelixScore = false;
					}
				}
				float gamma3;
				if(computeHelixScore){
					gamma3 = (float) foldSingleStranded_calcGamma3(len1,len2,seq,seq2,domain,i,j,Smatrix,SDmatrix,true);
				} else { 
					gamma3 = 0;
				}
				//Greedy algorithm: take the most minimal (proof: addititivity of delta G, optimization of a sum)
				Smatrix[i][j]=min(gamma1,gamma2,gamma3);
				//If there is a tie, use the following priority:
				if (gamma3 == Smatrix[i][j]){
					//Continuing helix, calcGamma3 autoincrements the helix length.
					//SDmatrix[i][j] = Math.max(0,SDmatrix[i+1][j-1])+1;
					//Leave the setting of the SDMatrix up to "calcGamma3". It must therefore run LAST in the above 3 seqs.
				} else if (gamma1 == Smatrix[i][j]){
					//Continuing loop, fix backtracking info
					sdMatrix(SDmatrix,i,j)[0] = Math.min(0,sdMatrix(SDmatrix,i+1,j)[0])-1; //Negative means longer loop
					sdMatrix(SDmatrix,i,j)[1] = Math.min(0,sdMatrix(SDmatrix,i+1,j)[1])-1; //Negative means longer loop
				} else if (gamma2 == Smatrix[i][j]){
					//Continuing loop, fix backtracking info
					sdMatrix(SDmatrix,i,j)[0] = Math.min(0,sdMatrix(SDmatrix,i,j-1)[0])-1; //Negative means longer loop
					sdMatrix(SDmatrix,i,j)[1] = Math.min(0,sdMatrix(SDmatrix,i,j-1)[1])-1; //Negative means longer loop
				} else {
					throw new RuntimeException("Assertion failure. foldSingleStranded_viaMatrix inner loop of filling.");
				}
				//Keep track of MFE.
				if (Smatrix[i][j] < score){
					score = Smatrix[i][j];
					bestI = i;
					bestJ = j;
				}
			}
		}
		
		//Traceback.
		double overCount = foldSingleStranded_traceBack(len1,len2,Smatrix,bestI,bestJ,seq,seq2,domain,domain_markings,!options.foldFullMatrix);
		
		if (debugLCSAlgorithm){
			/*
			for(int k = 0; k < len1; k++){
				for(int y = 0; y < len2; y++){
					System.out.printf(" (%3d,%3d)",sdMatrix(SDmatrix,k,y)[0],sdMatrix(SDmatrix,k,y)[1]);
				}
				System.out.println();
			}
			*/
			for(int k = 0; k < len1; k++){
				for(int y = 0; y < len2; y++){
					System.out.printf(" %4.8f",Smatrix[k][y]);
				}
				System.out.println();
			}
		}
		
		return score-overCount;
	}
	/**
	 * Performs the standard nussinov tracebacking, sans bifurcation tracing (thus, no stack).
	 */
	private double foldSingleStranded_traceBack(int len1, int len2, float[][] Smatrix, int bestI,
			int bestJ, DomainSequence seq, DomainSequence seq2,
			int[][] domain, int[][] domain_markings, boolean isSingleStrandFold) {
		
		int helixLength = 0;
		
		MFE_numBasesPaired = 0;
		MFE_longestHelixLength = 0;
		MFE_pointlist.clear();
		boolean inHelix = true;

		double overCount = 0;
		
		while(true){
			//System.out.println(inHelix+" "+bestI+" "+bestJ+" "+Arrays.toString(domain_markings[0]));
			//Break condition:
			//System.out.println(bestI+" "+bestJ);
			if (bestI>=len1 || bestJ < 0){
				break;
			}
			boolean isOnFringeOfMap;
			if (isSingleStrandFold){
				isOnFringeOfMap = bestJ<=bestI;
			} else {
				isOnFringeOfMap = bestI==len1-1 || bestJ==0;
			}
			MFE_pointlist.add(new Point(bestI,bestJ));
			if (isOnFringeOfMap){
				if (inHelix){
					if (Std.monomer.bindScore(base(seq,bestI,domain), base(seq2,bestJ,domain)) < 0){
						helixLength++;
						MFE_longestHelixLength = Math.max(MFE_longestHelixLength,helixLength);
					}
				}
			}
			if (inHelix && isOnFringeOfMap){ 
				if (Std.monomer.bindScore(base(seq,bestI,domain), base(seq2,bestJ,domain)) < 0){
					seq.mark(bestI, domain, domain_markings);
					seq2.mark(bestJ, domain, domain_markings);
				}
			}
			if (isOnFringeOfMap){
				break;
			}
			//inHelix = false;
			float gamma1 = Smatrix[bestI+1][bestJ];
			float gamma2 = Smatrix[bestI][bestJ-1];
			float gamma3 = Smatrix[bestI][bestJ];

			float best = min(gamma1,gamma2,gamma3);
			if (gamma1 == best){
				//Go there.
				inHelix = false;
				bestI++;	
			}
			else if (gamma2 == best){
				//Go there.
				inHelix = false;
				bestJ--;
			} else if (gamma3 == best){
				if (Std.monomer.bindScore(base(seq,bestI,domain), base(seq2,bestJ,domain)) < 0){
					seq.mark(bestI, domain, domain_markings);
					seq2.mark(bestJ, domain, domain_markings);
				}
				inHelix = true;
			}
			else {
				throw new RuntimeException("Assertion failure. foldSingleStranded_traceback in best check");
			}
			if (inHelix){
				//Mark condition:
				helixLength ++;
				MFE_longestHelixLength = Math.max(MFE_longestHelixLength,helixLength);
				//Go helix!
				bestI++;
				bestJ--;
				MFE_numBasesPaired++;
			} else {
				helixLength = 0;
			}
		}	
		if (MFE_longestHelixLength==1){ //Ended on a single base.
			overCount += foldSingleStranded_calcDummyScore;
		}
		return overCount;
	}

	private int MFE_longestHelixLength = -1, MFE_numBasesPaired = -1;
	private ArrayList<Point> MFE_pointlist = new ArrayList();
	/**
	 * WARNING: allocates a new matrix.
	 */
	public double[][] getNussinovMatrixScore(int len1, int len2) {
		double[][] nussinovScores = new double[len1][len2];
		for(int y = 0; y < len1; y++){
			for(int x = 0; x < len2; x++){
				nussinovScores[y][x] = sMatrix_shared[y][x];
			}
		}
		return nussinovScores;
	}
	/**
	 * WARNING: allocates a new list.
	 */
	public ArrayList<Point> getTraceback() {
		ArrayList<Point> toRet = new ArrayList<Point>();
		toRet.addAll(MFE_pointlist);
		return toRet;
	}
	private boolean debugLCSAlgorithm = false;
	private double foldSingleStranded_calcDummyScore = -.25;
	private double foldSingleStranded_calcGamma1(int i, int j, int len1, float[][] sMatrix, int[][][] sdMatrix) {
		//This is the number, if we are a "bulge" and defer to the helix in sMatrix[i+1][j].
		if (i+1>=len1){
			//Off the map.
			return 0.0;
		}
		double bulgeScore = sMatrix[i+1][j];
		//Be sure to remove dummyScore
		if (sdMatrix(sdMatrix,i+1,j)[0]==1){
			bulgeScore-=foldSingleStranded_calcDummyScore;
		}
		return bulgeScore;
	}
	private double foldSingleStranded_calcGamma2(int i, int j, float[][] sMatrix, int[][][] sdMatrix) {
		if (j-1<0){
			//Off the map.
			return 0.0;
		}
		double bulgeScore = sMatrix[i][j-1];
		//Be sure to remove dummyScore
		if (sdMatrix(sdMatrix,i,j-1)[0]==1){
			bulgeScore-=foldSingleStranded_calcDummyScore;
		}
		return bulgeScore;
	}
	/**
	 * Helix, + dummy score if new helix, - dummy score if 2nd base, + nn score is length >= 2.
	 * If beginning new helix, have to add the score of the last loop.
	 * 
	 * Both seq and seq2 should be in 5'-3' order.
	 */
	private double foldSingleStranded_calcGamma3(int len1, int len2, DomainSequence seq, DomainSequence seq2, int[][] domain, int i, int j, float[][] sMatrix, int[][][] sdMatrix, boolean writeToSD) {
		double dummyScore = foldSingleStranded_calcDummyScore;
		boolean onFringeOfMap = i+1>=len1 || j-1<0;
		if (Std.monomer.bindScore(base(seq,i,domain), base(seq2,j,domain)) < 0){
			//This is a pair. Extend helix
			if (writeToSD){
				sdMatrix(sdMatrix,i,j)[0] = Math.max(0,onFringeOfMap?0:sdMatrix(sdMatrix,i+1,j-1)[0])+1;
				sdMatrix(sdMatrix,i,j)[1] = sdMatrix(sdMatrix,i,j)[0]; //MUST set this > 0. Have to seperate loop counters from helix counters!
			}
			//New helix
			if (onFringeOfMap || sdMatrix(sdMatrix,i+1,j-1)[0]<=0){
				double addLoopOpeningPenalty = 0;
				if(!onFringeOfMap && sdMatrix(sdMatrix,i+1,j-1)[0]<0){
					//Ending a loop, of length > 0
					int leftLoopSize = -sdMatrix(sdMatrix,i+1,j-1)[0];
					int rightLoopSize = -sdMatrix(sdMatrix,i+1,j-1)[1];
				}
				return (onFringeOfMap?0:sMatrix[i+1][j-1])+dummyScore+addLoopOpeningPenalty; //Add dummy deltaG for starting helix
			}
			//Continuing old helix
			else {
				//get NN score.
				double nn = eParams.getNNdeltaG(base(seq,i+1, domain), base(seq2,j-1,domain), base(seq,i,domain), base(seq2, j,domain));
				double helixScore = sMatrix[i+1][j-1];
				if (sdMatrix(sdMatrix,i+1,j-1)[0]==1){
					//Remove dummy score
					helixScore -= dummyScore;
				}
				//Add nearest neighbor delta G
				helixScore += nn;
				return helixScore;
			}
		} else {
			//No helix. Extend both left and right bulges by one.
			if (writeToSD){
				sdMatrix(sdMatrix,i,j)[0] = Math.min(0,onFringeOfMap?0:sdMatrix(sdMatrix,i+1,j-1)[0])-1; //Negative means longer loop
				sdMatrix(sdMatrix,i,j)[1] = Math.min(0,onFringeOfMap?0:sdMatrix(sdMatrix,i+1,j-1)[1])-1;
			}
			if (onFringeOfMap){
				return 0.0;
			}
			//Ending old helix?
			if (sdMatrix(sdMatrix,i+1,j-1)[0]>0){
				if (sdMatrix(sdMatrix,i+1,j-1)[0]==1){
					//Remove dummy score
					return sMatrix[i+1][j-1]-dummyScore;
				} else {
					//Add terminal score.
					double terminalMismatch = eParams.getNNdeltaGterm(base(seq,i+1, domain), base(seq2,j-1,domain), base(seq,i,domain), base(seq2, j,domain));
					return sMatrix[i+1][j-1]+terminalMismatch;
				}
			} 
			//Continuing loop region
			else {
				return sMatrix[i+1][j-1];
			}
		}
	}
	private int[] sdMatrix(int[][][] sdMatrix, int i, int j) {
		return sdMatrix[i%2][j];
	}
	private float min(float gamma1, float gamma2, float gamma3) {
		return Math.min(Math.min(gamma1,gamma2),gamma3); 
	}
	
	//// Pair Probability functions - Currently not implemented or used.
	
	public void pairPrHybrid(double[][] pairsOut, DomainSequence seq1, DomainSequence seq2, int[][] domain) {
		if (pairPrMODE==NUPACK){
			pairPr_viaNUPACK(pairsOut, new DomainSequence[]{seq1,seq2}, domain);
		}
	}

	public void pairPrSS(double[][] pairsOut, DomainSequence seq, int[][] domain) {
		if (pairPrMODE==NUPACK){
			pairPr_viaNUPACK(pairsOut, new DomainSequence[]{seq}, domain);
		}
	}
	private static int ct = 0;
	private NupackRuntime NUPACKLINK = null;
	private static class NupackRuntime {
		public Process exec;
		public Scanner in;
		public PrintWriter out;
		public void finalize() {
			exec.destroy();
			in.close();
			out.close();
		}
	}
	public void pairPr_viaNUPACK(double[][] pairsOut, DomainSequence[] seqs, int[][] domain) {
		try {
			System.out.println("Going to nupack"+ct++);
			
			if (NUPACKLINK==null){
				NUPACKLINK = new NupackRuntime();
				NUPACKLINK.exec = Runtime.getRuntime().exec("/home/Benjamin/Code/C/nupack3.0/bin/pairs -T 37 -material dna -cutoff 0.001 -multi", new String[]{"NUPACKHOME=/home/Benjamin/Code/C/nupack3.0"});
				NUPACKLINK.out = new PrintWriter(new OutputStreamWriter(new BufferedOutputStream(NUPACKLINK.exec.getOutputStream())));
				NUPACKLINK.in = new Scanner(new BufferedInputStream(NUPACKLINK.exec.getInputStream()));
			}

			NUPACKLINK.out.println("output");
			NUPACKLINK.out.println(seqs.length);
			
			int N = 0;
			for(int i = 0; i < seqs.length; i++){
				int seqLen = seqs[i].length(domain);
				for(int k = 0; k < seqLen; k++){
					NUPACKLINK.out.print(Std.monomer.displayBase(base(seqs[i], k, domain)));
				}
				N += seqLen;
				NUPACKLINK.out.println();
			}
			//Clear probability matrix
			for(int i = 0; i < N; i++){
				for(int j = 0; j < N+1; j++){
					pairsOut[i][j] = 0;
				}
			}
			for(int i = 0; i < seqs.length; i++){
				NUPACKLINK.out.print((i+1)+" ");
			}
			NUPACKLINK.out.println();
			
			if (true){
				while(NUPACKLINK.in.hasNextLine()){
					String line = NUPACKLINK.in.nextLine();
					System.out.println(line);
					if (line.equals("DONE")){
						break;
					}
				}
			}
			
			Scanner in2;
			if (seqs.length==1){
				in2 = new Scanner(new File("output.ppairs"));
			} else {
				in2 = new Scanner(new File("output.epairs"));
			}
			while(in2.hasNextLine()){
				String line[] = in2.nextLine().trim().split("\\s+");
				if (line.length==3){
					if (line[0].startsWith("%")){
						continue;
					}
					int iB = new Integer(line[0])-1;
					int jB = new Integer(line[1])-1;
					pairsOut[iB][jB] = new Double(line[2]);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*
		for(int k = 0; k < length1; k++){
			for(int j = k; j < length1; j++){
				pairsOut[k][j] = Math.random();
				pairsOut[j][k] = Math.random(); 
			}	
		}
		*/
	}
	
}
