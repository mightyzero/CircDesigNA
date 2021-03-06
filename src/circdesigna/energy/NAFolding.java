/*
  Part of the CircDesigNA Project - http://cssb.utexas.edu/circdesigna
  
  Copyright (c) 2010-11 Ben Braun
  
  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/
package circdesigna.energy;

import circdesigna.GeneralizedInteractiveRegion;

/**
 * Describes objects which support many useful nucleic acid folding routines
 * 
 * mfe: Locate the minimum free energy structure formed by the interaction of 2 strands ("hybridization energy"), and
 * return its delta G. Or, in the one argument version, returns the MFE structure formed by a single strand.
 * 
 * mfeNoDiag: Locate the minimum free energy structure formed by the interaction of 2 equal length strands,
 * of only structures that are composed entirely of misaligned (i.e., not base-for-base) hybridization.
 * A baseline, dependent linearly on the length of the strands, is subtracted from the result.
 * 
 * In other words, a large negative value from this call means that the two strands can hybridize in a 
 * stable way without hybridizing base-for-base with one another. This is a hazard in systems, as it means
 * that domains can transiently mispair. 
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
	public double mfe(GeneralizedInteractiveRegion seq1, GeneralizedInteractiveRegion seq2, int[][] domain, int[][] domain_markings);
	public double mfe(GeneralizedInteractiveRegion domainSequence, int[][] domain, int[][] domain_markings);
	/**
	 * Unlike the other MFE prediction functions, this returns a value which is offset by an appropriate
	 * baseline for 50% G/C strand of the same length and type (DNA / RNA). 
	 * 
	 * That is, a negative value here means that there exists an more-than-expected misaligned pairing
	 * between the two equal length  
	 */
	public double mfeNoDiag(GeneralizedInteractiveRegion domainSequence, GeneralizedInteractiveRegion domainSequence2, int[][] domain, int[][] domain_markings);
	public double mfeStraight(GeneralizedInteractiveRegion domainSequence, GeneralizedInteractiveRegion domainSequence2, int[][] domain, int[][] domain_markings, int markLeft, int markRight, int joffset);
	
	//public void pairPr(double[][] pairsOut, DomainSequence seq1, DomainSequence seq2, int[][] domain);
	//public void pairPr(double[][] pairsOut, DomainSequence seq1, int[][] domain);
}