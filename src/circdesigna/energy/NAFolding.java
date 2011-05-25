package circdesigna.energy;

import edu.utexas.cssb.circdesigna.DomainSequence;

/**
 * Describes objects which support many useful nucleic acid folding routines
 * 
 * mfe: Locate the minimum free energy structure formed by the interaction of 2 strands ("hybridization energy"), and
 * return its delta G. Or, in the one argument version, returns the MFE structure formed by a single strand.
 * 
 * mfeStraight : the free energy of a straight helix of two aligned sequences. Not necessarily the MFE score!
 * The behavior of this method is complicated by the use of markings. This method can use bases for scoring which it is NOT allowed to "mark".
 * Use markLeft, markRight to denote the regions where the domain_markings array will be affected (indexed by the first sequence)
 * Use offsetJ to shift the alignment of the second sequence on the first. Positive values shift the second sequence to the right. 
 * 
 * pairPr: Fills in an appropriately sized double array of base pairing probabilities. After running,
 * the array[i][j] contains the probability that i of strand #1 is paired with j of strand #2. For convenience, the entire matrix will be
 * filled out and zeroed, up to the length of the two sequences (so the input matrix can be larger)
 * The one argument version does the same, but for pair probabilities of single stranded structures (a strand folding on itself)
 */
public interface NAFolding {
	public double mfe(DomainSequence seq1, DomainSequence seq2, int[][] domain, int[][] problemAreas);
	public double mfe(DomainSequence domainSequence, int[][] domain, int[][] domain_markings);
	public double mfeNoDiag(DomainSequence domainSequence, DomainSequence domainSequence2, int[][] domain, int[][] domain_markings);
	public double mfeStraight(DomainSequence domainSequence, DomainSequence domainSequence2, int[][] domain, int[][] domain_markings, int markLeft, int markRight, int joffset);
	
	public void pairPr(double[][] pairsOut, DomainSequence seq1, DomainSequence seq2, int[][] domain);
	public void pairPr(double[][] pairsOut, DomainSequence seq1, int[][] domain);
}