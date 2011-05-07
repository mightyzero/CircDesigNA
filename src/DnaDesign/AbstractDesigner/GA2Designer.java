package DnaDesign.AbstractDesigner;

import DnaDesign.DomainDesigner;


/**
 * An improved version of GADesigner, inspired by Tiny-GA in
 * "A field guide to genetic programming" Riccardo Poli
 * 
 * Implements MOGA.
 */
public class GA2Designer <T extends PopulationDesignMember<T>>  extends BlockDesigner <T> {
	//Parameters used in Tiny-GA
	private int POPSIZE;
	private int TSIZE;
	private int NUM_CHILDREN_PER_GENERATION;
	private double PMUT_PER_NODE; 
	private double CROSSOVER_PROB; 
	
	public GA2Designer(SingleMemberDesigner<T> SingleDesigner, ParetoSort<T> psort, boolean isMOGA) {
		super(SingleDesigner);
		this.psort = psort;
		this.isMOGA = isMOGA;
		
		//Settings used in the sin(x) example from the same book.
		CROSSOVER_PROB = .1;//.9; //Arbitrary!
		//PMUT_PER_NODE = .95;//.05; //Arbitrary!
		TSIZE = 2; //Arbitrary!
	}
	private boolean isMOGA;
	private ParetoSort psort;
	private T tempOffspring;
	
	@Override
	public void initialize(T init, int numCopies) {
		POPSIZE = numCopies;
		NUM_CHILDREN_PER_GENERATION = POPSIZE; //ARBITRARY!!!
		tempOffspring = init.designerCopyConstructor(numCopies+1);
		super.initialize(init, numCopies);
	}
	private int rnegtourn(int tsize) {
		return rtourn_(tsize,true);
	}
	private int rtourn(int tsize) {
		return rtourn_(tsize,false);
	}
	private int rtourn_(int tsize, boolean negativeTournament){
		int toRet = (int) (Math.random()*POPSIZE);
		double bestScore = SingleDesigner.getOverallScore(population_mutable[toRet]);
		for(int i = 0; i < tsize; i++){
			int competitor = (int) (Math.random()*POPSIZE);
			double competitorScore = SingleDesigner.getOverallScore(population_mutable[competitor]);
			if ((competitorScore < bestScore) ^ negativeTournament){
				toRet = competitor;
				bestScore = competitorScore;
			}
		}
		return toRet;
	}
	public void runBlockIteration_ (DomainDesigner runner, double endThreshold) {
		double maxFitness = 0;
		for(int i = 0; i < NUM_CHILDREN_PER_GENERATION; i++){
			//A new child will be born, by either SEXUAL or ASEXUAL reproduction.
			//This child will immediately knock out a randomly selected member of the population.
			if (Math.random() > CROSSOVER_PROB){ //Strange use of the word "Probability"
				//SEXUAL
				int parent1 = rtourn(TSIZE);
				int parent2 = rtourn(TSIZE);
				//On-the-fly crossover:
				SingleDesigner.fourPtCrossoverAndTest(population_mutable[parent1],population_mutable[parent2],tempOffspring);
			} else {
				//ASEXUAL
				int parent1 = rtourn(TSIZE);
				SingleDesigner.mutateAndTest(population_mutable[parent1], tempOffspring);
			}
			//Dedicate all penalties:
			
			//Knock out a random member. Note that the mutated member ALWAYS enters the population!
			int child = rnegtourn(TSIZE);
			
			if (population_mutable[child] == getBestPerformingChild() 
					&& SingleDesigner.getOverallScore(population_mutable[child]) < SingleDesigner.getOverallScore(tempOffspring)){
				//throw new RuntimeException("!");
				continue;
			}
			
			//Swap it out.
			T temp = tempOffspring;
			tempOffspring = population_mutable[child];
			population_mutable[child] = temp; 
		}
		int best = 0; double bestScore = Double.MAX_VALUE;
		for(int i = 0; i < population_mutable.length; i++){
			double score = SingleDesigner.getOverallScore(population_mutable[i]);
			if (score < bestScore){
				best = i;
				bestScore = score;
			}
		}
		setBestChild(population_mutable[best]);
		/*
		//Adjust for pareto fitness
		if (isMOGA){
			psort.adjustFitness(redist,0,populationSize*2,maxFitness+1,1);
		}
		//Redistribute members to mutable and backups
		Arrays.sort(redist);
		setBestChild(redist[0].myKey);
		for(int i = 0; i < populationSize; i++){
			population_mutable[i] = redist[i].myKey;
			population_backups[i] = redist[i+populationSize].myKey;
		}
		*/
	}
}