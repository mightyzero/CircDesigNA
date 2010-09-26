package DnaDesign.impl;

import static DnaDesign.DomainSequence.DNA_SEQ_FLAGSINVERSE;

import java.util.ArrayList;
import java.util.List;

import DnaDesign.DesignIntermediateReporter;
import DnaDesign.DnaDefinition;
import DnaDesign.DomainDesigner;
import DnaDesign.DomainDesigner_SharedUtils;
import DnaDesign.DomainSequence;
import DnaDesign.NAFolding;

public class DomainDesignerImpl extends DomainDesigner{


	/**
	 * Use the evaluator only to remove ss, don't care about dimers and so forth.
	 */
	private boolean designSSonly = false;
	/**
	 * Treshold ("green light" score) and weight for single stranded, hybridization scores.
	 * Todo: actual genetic algorithm that adjusts these over time.
	 */
	private double singleStrandT = 2.0, hybridStrandsT = 4.0;
	private double singleStrandW = 4.0, hybridStrandsW = 1.0;
	
	private NAFolding flI;
	/**
	 * @param foldingImpl<br>
	 * The folding score functions to utilize in evaluating solution candidates.
	 * @param designSSonly<br>
	 * 4 kinds of score functions are currently implemented: Validity, SingleStrandedAssurance, Crosstalk, Dimerization.
	 * If designSSonly is true, only SingleStrandedAssurance will be used; As a result, crosstalk and dimerization may occur
	 * in solution candidates.
	 */
	public DomainDesignerImpl(NAFolding foldingImpl, boolean designSSonly) {
		this.designSSonly = designSSonly;
		this.flI = foldingImpl;
	}

	public DomainDesignerImpl(FoldingImpl foldingImpl) {
		this(foldingImpl,false);
	}

	public class CrossInteraction extends ScorePenalty { 
		public CrossInteraction(DomainSequence ds, DomainSequence ds2, DesignIntermediateReporter dir, boolean invert){
			super(dir);
			this.ds = new DomainSequence[]{ds,ds2};
			for(DomainSequence q : getSeqs()){
				numDomains += q.numDomains;
			}
			numDomains /= getSeqs().length;
			chooseScore(dir);
			invertScore = invert;
		}
		private boolean invertScore;
		public int getPriority(){
			return 2;
		}
		private int numDomains;
		private DomainSequence[] ds;
		public double evalScoreSub(int[][] domain, int[][] domain_markings){
			double normal = (flI.pairscore(ds[0],ds[1],domain,null)-hybridStrandsT)*hybridStrandsW;
			if (invertScore){
				return -normal+50; //Scores will go negative very quickly.
			}
			return normal;
		}
		public boolean affectedBy(int domain) {
			return ds[0].contains(domain) || ds[1].contains(domain);
		}
		public DomainSequence[] getSeqs() {
			return ds;
		}
		public int getNumDomainsInvolved() {
			return numDomains;
		}
	}
	
	/**
	 * Penalize complementarity at the base of a hairpin loop
	 */
	public class HairpinOpeningPenalty extends ScorePenalty {
		public HairpinOpeningPenalty(DomainSequence dsL, DomainSequence dsR, DesignIntermediateReporter dir){
			super(dir);
			this.ds = new DomainSequence[]{dsL,dsR};
			chooseScore(dir);
		}
		public int getPriority(){
			return 0;
		}
		private DomainSequence[] ds;
		public double evalScoreSub(int[][] domain, int[][] domain_markings){
			for(int k = 0; k < 2; k++){
				if (DnaDefinition.bindScore(ds[0].base(k, domain),ds[1].base(ds[1].length(domain)-1-k, domain))>0){
					return 1;
				}
			}
			return 0;
		}
		public boolean affectedBy(int domain) {
			return (ds[0].domainList[0]& DNA_SEQ_FLAGSINVERSE)==domain ||
			(ds[1].domainList[ds[1].domainList.length-1]& DNA_SEQ_FLAGSINVERSE)==domain;
		}
		public DomainSequence[] getSeqs() {
			return ds;
		}
		public int getNumDomainsInvolved() {
			return 2;
		}
	}
	
	/**
	 * If we wish to use the validity checking as a penalty instead of an absolute condition,
	 * use this.
	 */
	public class ValidSequenceScore extends ScorePenalty {
		private List<DomainSequence> seqs;

		public ValidSequenceScore(List<DomainSequence> seqToSynthesize, DesignIntermediateReporter dir) {
			super(dir);
			seqs = seqToSynthesize;
			//chooseScore(dir); Can't show up. Uses everybody.
		}

		public boolean affectedBy(int domain) {
			return true;
		}

		public double evalScoreSub(int[][] domain, int[][] domain_markings) {
			double sum = 0;
			for(int i = 0; i < domain.length; i++){
				sum += flI.affectedSequenceInvalidScore(i, seqs, domain, domain_markings);
			}
			return sum;
		}

		public int getNumDomainsInvolved() {
			return 1;
		}

		public int getPriority() {
			if (rule_SeqRulesAreAbsolute==1){
				return 0;
			}
			return 2;
		}

		public DomainSequence[] getSeqs() {
			return new DomainSequence[0];
		}
	}
	
	//TODO: deliberate paring score.
	
	public class SelfFold extends ScorePenalty { 
		public SelfFold(DomainSequence ds, DesignIntermediateReporter dir){
			super(dir);
			this.ds = new DomainSequence[]{ds};
			for(DomainSequence q : getSeqs()){
				numDomains += q.numDomains;
			}
			numDomains /= getSeqs().length;
			chooseScore(dir);
		}
		private int numDomains;
		private DomainSequence[] ds;
		public double evalScoreSub(int[][] domain, int[][] domain_markings){
			return //pairscore(ds[0], ds[0], domain,null);
			(flI.foldSingleStranded(ds[0],domain,domain_markings)-singleStrandT)*singleStrandW;
		}
		public int getPriority(){
			return 1;
		}
		public boolean affectedBy(int domain) {
			return ds[0].contains(domain);
		}
		public DomainSequence[] getSeqs() {
			return ds;
		}
		public int getNumDomainsInvolved() {
			return numDomains;
		}
	}

	public List<ScorePenalty> listPenalties(
			List<DomainSequence> makeSingleStranded,
			ArrayList<DomainSequence> preventComplementarity,
			List<DomainSequence[]> hairpinLoops, DesignIntermediateReporter DIR) {
		int i,k;
		List<ScorePenalty> allScores = new ArrayList<ScorePenalty>();
		allScores.add(new ValidSequenceScore(makeSingleStranded,DIR));
		for(i = 0; i < hairpinLoops.size(); i++){
			DomainSequence[] ds = hairpinLoops.get(i);
			allScores.add(new HairpinOpeningPenalty(ds[0],ds[1],DIR));
		}
		for(i = 0; i < makeSingleStranded.size(); i++){
			DomainSequence ds = makeSingleStranded.get(i); //Only contains singlestranded sequences
			//Secondary structure avoidance.
			if (!DomainDesigner_SharedUtils.checkComplementary(ds, ds) || ALLOW_COMPLEMENTARY_SCORES){
				allScores.add(new SelfFold(ds,DIR));
			}
		}
		for(i = 0; i < preventComplementarity.size(); i++){
			DomainSequence ds = preventComplementarity.get(i); //singlestrands U hairpin insides.
			if (!designSSonly){
				//Dimerization
				if (!DomainDesigner_SharedUtils.checkComplementary(ds, ds)|| ALLOW_COMPLEMENTARY_SCORES){
					allScores.add(new CrossInteraction(ds,ds,DIR,false));
				}
				//Crosstalk.
				for(k = i+1; k < preventComplementarity.size(); k++){ //Do only upper triangle
					DomainSequence ds2 = preventComplementarity.get(k);
					if ((!DomainDesigner_SharedUtils.checkComplementary(ds, ds2)|| ALLOW_COMPLEMENTARY_SCORES) && ds != ds2){
						allScores.add(new CrossInteraction(ds2,ds,DIR,false));
					}
				}
			}
		}
		return allScores;
	}
}
