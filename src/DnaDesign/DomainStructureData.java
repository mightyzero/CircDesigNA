package DnaDesign;
import static DnaDesign.DomainSequence.DNA_COMPLEMENT_FLAG;
import static DnaDesign.DomainSequence.DNA_SEQ_FLAGSINVERSE;
import static DnaDesign.DnaDefinition.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DomainStructureData {
	public int[] domainLengths;
	public DomainStructure[] structures;
	public int[] domains;
	public String moleculeName;
	public static final int DEFAULT_TO_LENGTH = 8;
	/**
	 * Invertible.
	 */
	public Map<String, Integer> nameMap = new TreeMap();
	public String getDomainName(int domain){
		String postpend = ((domain & DNA_COMPLEMENT_FLAG)!=0?"*":"");
		for(Entry<String, Integer> q : nameMap.entrySet()){
			if (q.getValue()==(domain & DNA_SEQ_FLAGSINVERSE)){
				return q.getKey()+postpend;
			}
		}
		return ""+(domain & DNA_SEQ_FLAGSINVERSE)+postpend;
	}
	private Map<Integer, String> domainConstraints = new TreeMap();
	private Map<Integer, int[][]> compositionConstraints = new TreeMap();
	private static final int FLAG_CONSERVEAMINOS = 2;
	private Map<Integer, Integer> otherRuleFlags = new TreeMap();
	/**
	 * Returns the constraint for the NONCOMPLEMENTED version of this domain.
	 * You must handle complementation yourself in handling the constraints!
	 */
	public String getConstraint(int domain){
		domain &= DNA_SEQ_FLAGSINVERSE;
		if (domainConstraints.containsKey(domain)){
			return domainConstraints.get(domain);
		}
		StringBuffer wT = new StringBuffer();
		for(int k = 0; k < domainLengths[domain]; k++){
			wT.append("-");
		}
		return wT.toString();
	}
	
	/** 
	 * Pass in whole block, please
	 **/
	public static void readDomainDefs(String domainDefsBlock, DomainStructureData out){
		out.structures = null;
		out.nameMap.clear();
		out.domainConstraints.clear();
		out.otherRuleFlags.clear();
		ArrayList<Integer> domainLengths = new ArrayList();
		Scanner in = new Scanner(domainDefsBlock);
		int k = -1;
		while(in.hasNextLine()){
			String[] line = in.nextLine().trim().split("\\s+");
			if (line.length<=1){ //0 is sort of impossible though.
				continue;
			}
			k++;
			domainLengths.add(-1);//initialize length
			String domainID = line[0];
			if (domainID.length()==0){
				throw new RuntimeException("'Empty' Domain ID: on line "+k);
			}
			out.nameMap.put(domainID,k);
			int seqIndex = 1;
			int seqLen = -1;
			if (line[1].matches("\\d+")){
				//We have length map (optional)
				seqIndex = 2;
				domainLengths.set(k,seqLen = new Integer(line[1]));
			}
			//Sequence constraints...
			if (line.length>seqIndex){
				//Regions of characters enclosed in square bracket will be lowercased, meaning "lock".
				if (line[seqIndex].charAt(0)!='-'){
					StringBuffer parseBracketLowerCase = new StringBuffer();
					boolean inBracket = false;
					for(int e = 0; e < line[seqIndex].length(); e++){
						char kc = line[seqIndex].charAt(e);
						if (kc=='['){
							inBracket = true;
							continue;
						}
						if (kc==']'){
							if (!inBracket){
								//Oops, stack underflow.
								throw new RuntimeException("Stack Underflow of nonconstraint bracket: "+line[seqIndex]);
							}
							inBracket = false;
							continue;
						}
						if (Character.toLowerCase(kc)=='u'){
							kc = 'T'; // rna / dna treated equally.
						}
						if (!inBracket){ //Things in brackets are allowed to change.
							kc = Character.toLowerCase(kc);
						} else {
							kc = Character.toUpperCase(kc);
						}
						parseBracketLowerCase.append(kc);
					}
					line[seqIndex] = parseBracketLowerCase.toString();
					//Ok! load the constraint. Default to "unconstrained".
					if (line[seqIndex].equalsIgnoreCase("TBD")){
						//Lol, silly format.
					} else {
						out.domainConstraints.put(k,line[seqIndex]);
						domainLengths.set(k,seqLen = line[seqIndex].length());
					}
					seqIndex++;
				}
				//Do we have a protein flag?
				int flagSum = 0;
				Pattern decodeArg = Pattern.compile("\\-(\\w+)(\\((.*)\\))?");
				for(int flag = seqIndex; flag < line.length; flag++){
					Matcher m = decodeArg.matcher(line[flag]);
					if(m.find()){
						String paramName = null;
						try {
							paramName = m.group(1);
						} catch (Throwable e){
							throw new RuntimeException("Invalid parameter name @ k ");
						}
						try {
							String args = m.group(3);
							if (paramName.equalsIgnoreCase("p")){
								if (seqLen%3!=0){
									throw new RuntimeException("Domain "+domainID+" not a valid protein sequence - length not a multiple of 3");
								}
								flagSum |= FLAG_CONSERVEAMINOS;
							}
							if (paramName.equalsIgnoreCase("seq")){
								out.compositionConstraints.put(k, parseSequenceComposition(args));
							}
						} catch (Throwable e){
							throw new RuntimeException("Invalid args to '-"+paramName+"': "+e.getMessage());
						}
					}
				}
				out.otherRuleFlags.put(k,flagSum);
			}
			if (seqLen==-1){
				throw new RuntimeException("Assertion error - no length for domain '"+domainID+"'");
			}
			if (seqLen < 2){
				throw new RuntimeException("1-base domains not allowed for now.");
			}
		}
		out.domainLengths = new int[domainLengths.size()];
		for(k = 0; k < domainLengths.size(); k++){
			out.domainLengths[k] = domainLengths.get(k);
		}
	}
	
	//Tested and verified. Group 1 : Domain, with comp flag, Group 2: Structural flag
	private static final Pattern regexp = Pattern.compile("(\\w+\\*?)(.*?)($|[\\|\\}\\[]+)");
	
	public static void readStructure(String moleculeName, String dnaString, DomainStructureData out){
		out.moleculeName = moleculeName;
		out.domains = null;
		out.structures = null;
		Matcher m = regexp.matcher(dnaString);
		int whichDomain = 0, seqId = 0;
		LinkedList<Integer> parens = new LinkedList();
		TreeMap<Integer, Integer> lockMap = new TreeMap();
		TreeMap<Integer,DomainStructure> out2 = new TreeMap();
		TreeMap<Integer, Integer> whichDomainsInOrder = new TreeMap();
		int highestDomainUsed = -1;
		while(m.find()){
			try {
				String domainName = m.group(1);
				//Decode which domain
				int domainNameL = domainName.length();
				if (domainName.endsWith("*")){
					domainNameL --;
				}
				int numberDomain = out.lookupDomainName(domainName.substring(0,domainNameL));
				int numberDomain2 = numberDomain;
				if (numberDomain2 < 0){
					//TODO: Domain targetted exceptions?
					throw new RuntimeException("Invalid domain: "+domainName);
				}
				if (domainName.endsWith("*")){
					numberDomain2 |= DNA_COMPLEMENT_FLAG;
				}
				highestDomainUsed = Math.max(highestDomainUsed,numberDomain);
				whichDomainsInOrder.put(whichDomain,numberDomain2);

				String match = m.group(2);
				if (match==null){
					match = "";
				}
				String struct = match;
				if (struct.length()==0 || struct.contains(".")){
					out2.put(seqId,new SingleStranded(whichDomain));
				} else if (struct.contains("(")){
					out2.put(seqId,new SingleStranded(whichDomain));
					parens.add(seqId);
				} else if (struct.contains(")")){
					if (parens.isEmpty()){
						throw new RuntimeException("Empty Stack: "+m.group(0));
					}
					int mP = parens.removeLast();
					DomainStructure remove = out2.remove(mP);
					if (!(remove instanceof SingleStranded)){
						throw new RuntimeException("?huh?");
					}
					//Replace with a hairpinStem.
					HairpinStem create = new HairpinStem(remove.sequencePartsInvolved[0],whichDomain);
					SortedMap<Integer, DomainStructure> subMap = out2.subMap(mP, seqId);
					ArrayList<Integer> holder = new ArrayList();
					for(DomainStructure q : subMap.values()){
						create.addSubStructure(q);
					}
					for(int q : subMap.keySet()){
						holder.add(q);
					}
					for(int p : holder){
						out2.remove(p);
					}
					out2.put(seqId, create);
				}
				if (m.group(3)!=null){
					if (m.group(3).contains("}")){
						//3- end. 
						out2.put(++seqId,new ThreePFivePOpenJunc());
					}
				}
			} finally {
				whichDomain++;
				seqId++;
			}
		}
		if (highestDomainUsed<0){
			throw new RuntimeException("Empty strand; no domains");
		}
		//Debug
		int numDomains = whichDomain--;
		//This is dependent on the input sequence. Not on Domain Definitions.
		out.domains = new int[numDomains];
		for(Entry<Integer, Integer> q : whichDomainsInOrder.entrySet()){
			out.domains[q.getKey()] = q.getValue();
		}
		out.structures = new DomainStructure[out2.size()];
		int i = 0;
		for(DomainStructure struct : out2.values()){
			out.structures[i++]=struct;
			if (struct instanceof HairpinStem){
				((HairpinStem)struct).handleSubConformation(out.domainLengths,out.domains);
			}
		}
	}
	private static int[] expandToLength(int[] old, int newSize, int fillValue){
		int[] newOld = new int[newSize];
		Arrays.fill(newOld,fillValue);
		if (old!=null){
			System.arraycopy(old,0,newOld,0,Math.min(old.length,newOld.length));
		}
		return newOld;
	}
	
	private int lookupDomainName(String substring) {
		if (!nameMap.containsKey(substring)){
			return -1;
		}
		return nameMap.get(substring);
	}

	public static class DomainStructure {
		public DomainStructure (int ... sequencePartsInvolved){
			this.sequencePartsInvolved = sequencePartsInvolved;
		}
		//Numbering is 0 - length of input structure sequence.
		public int[] sequencePartsInvolved;
		public ArrayList<DomainStructure> subStructure = new ArrayList();
		public float random0 = (float)Math.random();
		public void addSubStructure(DomainStructure q) {
			subStructure.add(q);
		}
		/**
		 * +1 for each domain, +1 for each 3' end.
		 */
		public int countLabeledElements() {
			int ret = 0;
			ret += this.sequencePartsInvolved.length;
			for(DomainStructure q : subStructure){
				ret += q.countLabeledElements();
			}
			if (this instanceof ThreePFivePOpenJunc){
				ret++;
			}
			return ret;
		}
	}
	public static class HairpinStem extends DomainStructure {
		public HairpinStem(int ... whichDomain) {
			super(whichDomain);
		}
		
		public int leftRightBreak = -1;
		public int innerCurveCircumference = 0;
		
		public void handleSubConformation(int[] domainLengths, int[] domains) {
			loop:for(DomainStructure q : subStructure){
				if (q instanceof HairpinStem){
					((HairpinStem)q).handleSubConformation(domainLengths,domains);
				}
			}
			boolean isConnected = true;
			if (subStructure.size()>1){
				int index = 0;
				loop:for(DomainStructure q : subStructure){
					if (q instanceof ThreePFivePOpenJunc){
						isConnected = false;
						leftRightBreak = index;
						break loop;
					}
					index++;
				};
			}
			if (isConnected){
				if (subStructure.size()==1 && (subStructure.get(0) instanceof HairpinStem)){
					innerCurveCircumference = 0; //Just continue the stem
				} else {
					innerCurveCircumference = 0; //The "opening" of the hairpin takes up some of the ring
					loop:for(DomainStructure q : subStructure){
						if (q instanceof HairpinStem){
							innerCurveCircumference += 2; 
						} else if (q instanceof SingleStranded){
							for(int p : q.sequencePartsInvolved){
								innerCurveCircumference += domainLengths[domains[p] & DNA_SEQ_FLAGSINVERSE];
							}
						}
					}
				}
			}
		}
		
		public String toString(){
			StringBuffer sb = new StringBuffer();
			String line = System.getProperty("line.separator");
			sb.append(super.toString());
			sb.append(" ");
			if (innerCurveCircumference > 0)
				sb.append(innerCurveCircumference);
			for(int i = 0; i < subStructure.size(); i++){
				String[] sub = subStructure.get(i).toString().split(line);
				for(String d : sub){
					if (leftRightBreak==-1){
						sb.append(line+">"+d);
					} else {
						if (i > leftRightBreak){
							sb.append(line+"R"+d);
						} else {
							sb.append(line+"L"+d);
						}
					}
				}
			}
			return sb.toString();
		}
	}
	public static class SingleStranded extends DomainStructure {
		public SingleStranded(int ... whichDomain) {
			super(whichDomain);
		}
		public void addSubStructure(DomainStructure q) {
			throw new RuntimeException("Cannot add to Single Stranded Structures");
		}
	}
	public static class ThreePFivePOpenJunc extends DomainStructure{
		public ThreePFivePOpenJunc() {
			super();
		}
		//When 2 dna strands are combined, don't connect them (i.e., don't make it look like ligation)

		public void addSubStructure(DomainStructure q) {
			throw new RuntimeException("Cannot add to 3'-5' junc");
		}
	}	
	public int getDomainLength(int i) {
		return domainLengths[domains[i]&DNA_SEQ_FLAGSINVERSE];
	}

	boolean maintainAminos(int k) {
		Integer integer = otherRuleFlags.get(k);
		if (integer==null){
			return false;
		}
		return (integer & FLAG_CONSERVEAMINOS)!=0;
	}

	/**
	 * -2 means not specified. Otherwise, a -1 is deliberately fed in by the user.
	 */
	public int getMaxComponent(int i, int base) {
		int[][] minmax = compositionConstraints.get(i);
		if (minmax==null){
			return -2;
		}
		int[] max = minmax[1];
		return max[base];
	}
	public int getMinComponent(int i, int base) {
		int[][] minmax = compositionConstraints.get(i);
		if (minmax==null){
			return -2;
		}
		int[] min = minmax[0];
		return min[base];
	}
	/**
	 * Parses an argument string of the form
	 * <base>,<min amount>,<maxamount>,<base2> ... so forth
	 * where -1 means 'no bound'
	 */
	private static int[][] parseSequenceComposition(String args) {
		int[] max = new int[DNAFLAG_ADD];
		int[] min = new int[DNAFLAG_ADD];
		Arrays.fill(max,-2);
		Arrays.fill(min,-2);
		String[] array = args.split(",");
		if (array.length%3!=0){
			throw new RuntimeException("Each contraint has 3 parts: base, min, and max");
		}
		for(int k = 0; k < array.length; k+=3){
			if (array[k].length()!=1){
				throw new RuntimeException("Invalid base: "+array[k]);
			}
			int base = DnaDefinition.decodeBaseChar(array[k].charAt(0));
			if (base == 0 || base >= DNAFLAG_ADD){
				throw new RuntimeException("Invalid base: "+array[k]);
			}
			//pure base.
			int num1 = new Integer(array[k+1]);
			int num2 = new Integer(array[k+2]);
			if (num1 < -1 || num2 < -1){
				throw new RuntimeException("Bound values must be >= -1. -1 means no bound.");
			}
			if (num2 !=-1 && num1 != -1 && num2 < num1){
				throw new RuntimeException("Invalid bound: max < min");
			}
			if (min[base]!=-2 || max[base]!=-2){
				throw new RuntimeException("Duplicate bounds for "+array[k]);
			}
			min[base] = num1;
			max[base] = num2;
		}
		return new int[][]{min,max};
	}
}
