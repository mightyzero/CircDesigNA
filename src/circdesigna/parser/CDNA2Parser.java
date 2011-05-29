package circdesigna.parser;

import java.util.ArrayList;
import beaver.*;

/**
 * This class is a LALR parser generated by
 * <a href="http://beaver.sourceforge.net">Beaver</a> v0.9.6.1
 * from the grammar specification "CDNA2.grammar".
 */
public class CDNA2Parser extends Parser {
	static public class Terminals {
		static public final short EOF = 0;
		static public final short DOMAINNAME = 1;
		static public final short LPAREN = 2;
		static public final short RPAREN = 3;
		static public final short MULT = 4;
		static public final short DOT = 5;
		static public final short LSQBRACE = 6;
		static public final short RCBRACE = 7;
	}

	static final ParsingTables PARSING_TABLES = new ParsingTables(
		"U9nrZi4EWp0CGnymM0lzQ7uQjh4num873YMI5Jcs5GD9ioOUj7GywGaqH6e6CAlDibPP2xv" +
		"NTZapiayyDsC1dlRA9Rtkl8LiuiZyzTDNYpuvhLNllLVCtzqckE4TSljX0o2594q=");

	public ArrayList parenStack = new ArrayList();
	public ArrayList braceStack = new ArrayList();
	protected void recoverFromError(Symbol token, TokenStream in) throws java.io.IOException, Parser.Exception
	{
		throw new RuntimeException("Syntax error.");
	}

	private final Action[] actions;

	public CDNA2Parser() {
		super(PARSING_TABLES);
		actions = new Action[] {
			Action.NONE,  	// [0] opt$molecule = 
			Action.RETURN,	// [1] opt$molecule = molecule
			new Action() {	// [2] molecule = opt$molecule.b DOMAINNAME.n
				public Symbol reduce(Symbol[] _symbols, int offset) {
					final Symbol _symbol_b = _symbols[offset + 1];
					final ArrayList b = (ArrayList) _symbol_b.value;
					final Symbol _symbol_n = _symbols[offset + 2];
					final String n = (String) _symbol_n.value;
					 ArrayList p; if (b==null) p = new ArrayList(); else p = b; CDNA2Token.Domain neu = new CDNA2Token.Domain(n); p.add(neu); return new Symbol(p);
				}
			},
			new Action() {	// [3] molecule = molecule.a LPAREN
				public Symbol reduce(Symbol[] _symbols, int offset) {
					final Symbol _symbol_a = _symbols[offset + 1];
					final ArrayList a = (ArrayList) _symbol_a.value;
					 ((CDNA2Token.Domain)a.get(a.size()-1)).setOpen(parenStack); return new Symbol(a);
				}
			},
			new Action() {	// [4] molecule = molecule.a RPAREN
				public Symbol reduce(Symbol[] _symbols, int offset) {
					final Symbol _symbol_a = _symbols[offset + 1];
					final ArrayList a = (ArrayList) _symbol_a.value;
					 ((CDNA2Token.Domain)a.get(a.size()-1)).setClosed(parenStack); return new Symbol(a);
				}
			},
			new Action() {	// [5] molecule = molecule.a MULT
				public Symbol reduce(Symbol[] _symbols, int offset) {
					final Symbol _symbol_a = _symbols[offset + 1];
					final ArrayList a = (ArrayList) _symbol_a.value;
					 ((CDNA2Token.Domain)a.get(a.size()-1)).setComplement(); return new Symbol(a);
				}
			},
			new Action() {	// [6] molecule = molecule.a DOT
				public Symbol reduce(Symbol[] _symbols, int offset) {
					final Symbol _symbol_a = _symbols[offset + 1];
					final ArrayList a = (ArrayList) _symbol_a.value;
					 ((CDNA2Token.Domain)a.get(a.size()-1)).setSingleStranded(); return new Symbol(a);
				}
			},
			new Action() {	// [7] molecule = opt$molecule.b LSQBRACE
				public Symbol reduce(Symbol[] _symbols, int offset) {
					final Symbol _symbol_b = _symbols[offset + 1];
					final ArrayList b = (ArrayList) _symbol_b.value;
					 ArrayList p; if (b==null) p = new ArrayList(); else p = b; p.add(new CDNA2Token.FivePrimeEnd(braceStack)); return new Symbol(p);
				}
			},
			new Action() {	// [8] molecule = molecule.a RCBRACE
				public Symbol reduce(Symbol[] _symbols, int offset) {
					final Symbol _symbol_a = _symbols[offset + 1];
					final ArrayList a = (ArrayList) _symbol_a.value;
					 a.add(new CDNA2Token.ThreePrimeEnd(braceStack)); return new Symbol(a);
				}
			},
			new Action() {	// [9] declaration = DOMAINNAME.n molecule.q
				public Symbol reduce(Symbol[] _symbols, int offset) {
					final Symbol _symbol_n = _symbols[offset + 1];
					final String n = (String) _symbol_n.value;
					final Symbol _symbol_q = _symbols[offset + 2];
					final ArrayList q = (ArrayList) _symbol_q.value;
					 ArrayList p = new ArrayList(); p.add(n); p.addAll(q); return new Symbol(p);
				}
			}
		};

	
		report = new Parser.Events(){
			public void scannerError(Scanner.Exception e)
			{
				throw new RuntimeException(e.getMessage());
			}
			public void syntaxError(Symbol e)
			{
				throw new RuntimeException("Unexpected \""+e.value+"\"");
			}
		};
	}

	protected Symbol invokeReduceAction(int rule_num, int offset) {
		return actions[rule_num].reduce(_symbols, offset);
	}
}