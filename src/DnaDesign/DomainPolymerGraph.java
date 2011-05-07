package DnaDesign;

import static DnaDesign.DomainSequence.DNA_COMPLEMENT_FLAG;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A data structure for representing DNA complexes. This one is based on the circular polymer graph.
 * @author Benjamin
 */
public class DomainPolymerGraph implements AbstractComplex{
	private DomainPolymerGraph(){
	}
	public DomainPolymerGraph(DomainDefinitions domainDefs){
		this.domainDefs = domainDefs;
		data = new BasePolymerGraph();
	}	
	public DomainPolymerGraph getRotation(String moleculeName, int rotation){
		DomainPolymerGraph toRet = new DomainPolymerGraph();
		toRet.domainDefs = domainDefs;
		toRet.data = new RotatedPolymerGraph(data,rotation);
		return toRet;
	}
	private DomainDefinitions domainDefs;
	private AbstractPolymerGraph data;
	public String moleculeName;
	public int length(){
		return data.length();
	}
	public int getDomain(int position){
		return data.getDomain(position);
	}
	public int getDomainPair(int position){
		return data.getDomainPair(position);
	}
	
	private abstract static class AbstractPolymerGraph {
		public abstract BasePolymerGraph getCyclicIndependentForm();
		public abstract int length();
		/**
		 * -1 means 3' end
		 */
		public abstract int getDomain(int position);
		/**
		 * -1 if not paired
		 */
		public abstract int getDomainPair(int position);
	}
	public class BasePolymerGraph extends AbstractPolymerGraph{
		//Indexed by position in the string.
		//Domain -1 is a 3' end.
		private int[] domains;
		//-1 means "not paired".
		private int[] domain_pairs;
		public BasePolymerGraph getCyclicIndependentForm() {
			return this;
		}
		public int getDomain(int position) {
			return domains[position];
		}
		public int getDomainPair(int position) {
			return domain_pairs[position];
		}
		public int length() {
			return domains.length;
		}
	}
	public class RotatedPolymerGraph extends BasePolymerGraph{
		private BasePolymerGraph base;
		private int rotation;
		public RotatedPolymerGraph(AbstractPolymerGraph base, int rotation){
			this.base = base.getCyclicIndependentForm();
			if (base instanceof RotatedPolymerGraph){
				rotation += ((RotatedPolymerGraph)base).rotation;
				rotation = wrap(rotation, base.length());
			}
			this.rotation = rotation;
		}
		public BasePolymerGraph getCyclicIndependentForm() {
			return base;
		}
		public int getDomain(int position) {
			return base.domains[wrap(position+rotation,base.domains.length)];
		}
		private int wrap(int i, int length) {
			while (i < 0){
				i += length;
			}
			i %= length;
			return i;
		}
		public int getDomainPair(int position) {
			int originalPair = base.domain_pairs[wrap(position+rotation,base.domains.length)];
			if (originalPair<0){
				return originalPair;
			}
			int newPair = wrap(originalPair-rotation,base.domains.length);
			return newPair;
		}
		public int length() {
			return base.domains.length;
		}
	}

	public static void readStructure(String moleculeName, String dnaString, DomainPolymerGraph out2){
		out2.moleculeName = moleculeName;
		
		//First disregard all whitespace.
		//This does not really require regular expressions.
		dnaString = dnaString.replaceAll("\\s","");
		
		BasePolymerGraph out = out2.data.getCyclicIndependentForm();
		
		//Tested and verified. Group 2 : Domain, with comp flag, Group 1 + Group 3: Structural flag
		final Pattern regexp = Pattern.compile("(.*?)(\\w+\\*?)(.*?)($|[\\|\\}\\[]+)");
		out.domains = null;
		out.domain_pairs = null;

		Matcher m = regexp.matcher(dnaString);

		LinkedList<Integer> stack = new LinkedList();
		ArrayList<Integer> domains_tmp = new ArrayList();
		ArrayList<Integer> domain_pairs_tmp = new ArrayList();
		
		while(m.find()){
			String domainName = m.group(2);
			//Decode which domain, extract the "star" character (reverse complement flag)
			int domainNameL = domainName.length();
			if (domainName.endsWith("*")){
				domainNameL --;
			}
			int numberDomain = out2.domainDefs.lookupDomainName(domainName.substring(0,domainNameL));
			int numberDomain2 = numberDomain;
			if (numberDomain2 < 0){
				throw new RuntimeException("Invalid domain: "+domainName);
			}
			if (out2.domainDefs.domainLengths[numberDomain2] == 0){
				//Ignore domains of length 0 when parsing the structures.
			} else {
				if (domainName.endsWith("*")){
					numberDomain2 |= DNA_COMPLEMENT_FLAG;
				}

				String match = m.group(1)+m.group(3);
				if (match==null){
					match = "";
				}
				String struct = match;

				int thisIndex = domains_tmp.size();
				domains_tmp.add(numberDomain2);
				domain_pairs_tmp.add(-1); //fill with -1s, overwrite if adding pair.
				if (struct.contains("(")){
					stack.push(thisIndex);
				} else if (struct.contains(")")){
					if (stack.isEmpty()){
						throw new RuntimeException("Empty stack "+m.group(0));
					}
					int openParens = stack.pop();
					domain_pairs_tmp.set(openParens,thisIndex);
					domain_pairs_tmp.set(thisIndex,openParens);
				} else {
					//Do nothing.
				}
			}

			final String delim = m.group(4);
			if (delim!=null){
				//Checking valid molecule "enders"
				if (delim.contains("[")){
					for(int k = 0; k < delim.length(); k++){
						//Only valid use of '[' is after '}', except for the first character (taken care of above)
						if (delim.charAt(k)=='[' && (k==0 || delim.charAt(k-1)!='}')){
							throw new RuntimeException("Invalid structure: Use } before [.");
						}
					}
				}
				if (delim.matches("(.*)\\[(.*)\\}(.*)")){
					throw new RuntimeException("Empty strand; no domains");
				}
				if (delim.contains("}")){
					if (domains_tmp.isEmpty() || domains_tmp.get(domains_tmp.size()-1)==-1){
						//Empty strand.
					} else {
						//3- end.
						domains_tmp.add(-1);
						domain_pairs_tmp.add(-1); //fill with -1s, overwrite if adding pair.
					}
				}
			}
		}

		//This is dependent on the input sequence. Not on Domain Definitions.
		out.domains = new int[domains_tmp.size()];
		out.domain_pairs = new int[domains_tmp.size()];
		for(int k = 0; k < out.domains.length; k++){
			out.domains[k] = domains_tmp.get(k);
			out.domain_pairs[k] = domain_pairs_tmp.get(k);
		}
		
		//Erase rotation afterwards.
		out2.data = out;
		return;
	}		

	public String toString(){
		return "Polymer graph of "+moleculeName;
	}

	public String getStructureString(){
		String lineS = System.getProperty("line.separator");
		StringBuffer sb = new StringBuffer();
		int depth = 0;
		boolean needsOpenBracket = true, needsCloseItem = false;
		for(int k = 0; k < length(); k++){
			if (needsOpenBracket){
				sb.append("[");
				needsOpenBracket = false;
			}
			int domain = getDomain(k);
			if (domain>=0){
				if (needsCloseItem){
					sb.append("|");
					needsCloseItem = false;
				}
				//getDomainName does the works - adds an asterix, looks up the name, etc.
				sb.append(domainDefs.getDomainName(domain));
				int pair = getDomainPair(k);
				if (pair >= 0){
					if (pair < k){
						//the close parens
						sb.append(")");
					} else {
						sb.append("(");	
					}
				}
				needsCloseItem = true;
			} else {
				sb.append("}");
				needsOpenBracket = true;
				needsCloseItem = false;
			}
		}
		if (needsCloseItem){
			sb.append("}");
		}
		return sb.toString();
	}

	/**
	 * Returns the positions of all the 3' ends.
	 */
	public Collection<Integer> getStrandRotations() {
		ArrayList<Integer> toRet = new ArrayList<Integer>();
		//Length -1, because the last one doesn't count.
		for(int k = 0; k < length()-1; k++){
			if (getDomain(k)==-1){
				toRet.add(k+1);
			}
		}
		return toRet;
	}
	
	public String getMoleculeName() {
		return moleculeName;
	}

	public DomainDefinitions getDomainDefs() {
		return domainDefs;
	}
}
