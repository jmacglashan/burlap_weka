package burlap.behavior.functionapproximation.supervised;

import burlap.behavior.valuefunction.ValueFunction;
import burlap.behavior.functionapproximation.dense.DenseStateFeatures;
import burlap.datastructures.WekaInterfaces;
import burlap.mdp.core.state.State;
import weka.classifiers.Classifier;
import weka.classifiers.lazy.IBk;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SelectedTag;
import weka.core.neighboursearch.KDTree;

import java.util.List;

/**
 * A class for using supervised learning algorithms provided by the Weka library for fitted value iteration. This class
 * takes a generator for Weka {@link weka.classifiers.Classifier} objects (specified with the {@link WekaVFATrainer.WekaClassifierGenerator}
 * interface and a {@link DenseStateFeatures} to turn BURLAP {@link State} objects into
 * feature vectors usable by Weka.
 *
 * <p>
 * This class also provides the static method {@link #getKNNTrainer(DenseStateFeatures, int)}
 * for construction of the Weka instance-based regression algorithm {@link weka.classifiers.lazy.IBk},
 * since instance-based methods have convergence guarantees for fitted value iteration.
 * @author James MacGlashan.
 */
public class WekaVFATrainer implements SupervisedVFA{

	/**
	 * The generator of the Weka {@link weka.classifiers.Classifier} to use for training.
	 */
	protected WekaClassifierGenerator baseClassifier;

	/**
	 * The {@link DenseStateFeatures} used to convert BURLAP {@link State} objects to feature vectors.
	 */
	protected DenseStateFeatures fvGen;


	/**
	 * Initializes.
	 * @param baseClassifier The generator of the Weka {@link weka.classifiers.Classifier} to use for training.
	 * @param fvGen The {@link DenseStateFeatures} used to convert BURLAP {@link State} objects to feature vectors.
	 */
	public WekaVFATrainer(WekaClassifierGenerator baseClassifier, DenseStateFeatures fvGen){
		this.baseClassifier = baseClassifier;
		this.fvGen  = fvGen;
	}


	@Override
	public ValueFunction train(List<SupervisedVFA.SupervisedVFAInstance> trainingData) {


		Instances dataset = WekaInterfaces.getInstancesShell(trainingData.get(0).s, this.fvGen, trainingData.size());

		for(SupervisedVFA.SupervisedVFAInstance td : trainingData){
			dataset.add(WekaInterfaces.getInstance(td.s, this.fvGen, td.v, dataset));
		}

		Classifier classifier = this.baseClassifier.generateClassifier();
		try {
			classifier.buildClassifier(dataset);
		} catch(Exception e) {
			e.printStackTrace();
		}



		return new WekaVFA(this.fvGen, classifier);
	}


	/**
	 * Creates a standard supervised VFA trainer that uses Weka's {@link weka.classifiers.lazy.IBk} instance-based algorithm with
	 * a KD-tree and 1-distance similarity measure.
	 * @param fvGen the {@link DenseStateFeatures} for converting the BURLAP state into a feature vector usable by Weka.
	 * @param k the number of nearest neighbors uses in the regression algorithm.
	 * @return the {@link WekaVFATrainer} for Weka's {@link weka.classifiers.lazy.IBk} algorithm.
	 */
	public static WekaVFATrainer getKNNTrainer(DenseStateFeatures fvGen, final int k){

		WekaClassifierGenerator generator = new WekaClassifierGenerator() {
			@Override
			public Classifier generateClassifier() {
				IBk classifer = new IBk();
				classifer.setNearestNeighbourSearchAlgorithm(new KDTree());
				classifer.setKNN(k);
				classifer.setDistanceWeighting(new SelectedTag(IBk.WEIGHT_SIMILARITY, IBk.TAGS_WEIGHTING));
				return classifer;
			}
		};

		WekaVFATrainer trainer = new WekaVFATrainer(generator, fvGen);
		return trainer;

	}


	/**
	 * A class for predicting state values by using a trained Weka {@link weka.classifiers.Classifier}.
	 */
	public static class WekaVFA implements ValueFunction{

		/**
		 * The {@link DenseStateFeatures} used to convert BURLAP {@link State} objects to feature vectors.
		 */
		protected DenseStateFeatures fvGen;

		/**
		 * The Weka {@link weka.classifiers.Classifier} used to predict state values.
		 */
		protected Classifier classifier;


		/**
		 * Initializes.
		 * @param fvGen The {@link DenseStateFeatures} used to convert BURLAP {@link State} objects to feature vectors.
		 * @param classifier The Weka {@link weka.classifiers.Classifier} used to predict state values.
		 */
		public WekaVFA(DenseStateFeatures fvGen, Classifier classifier) {
			this.fvGen = fvGen;
			this.classifier = classifier;
		}

		@Override
		public double value(State s) {
			double [] vec = fvGen.features(s);
			Instances dataset = WekaInterfaces.getInstancesShell(vec, 1);
			Instance inst = WekaInterfaces.getInstance(vec, 0., dataset);
			double prediction = 0.;
			try {
				prediction = classifier.classifyInstance(inst);
			} catch(Exception e) {
				throw new RuntimeException("WekaVFA could not produce prediction for instance. Returned message:\n" + e.getMessage());
			}
			return prediction;
		}
	}

	/**
	 * An interface for generating Weka {@link weka.classifiers.Classifier} objects to use for training.
	 */
	public interface WekaClassifierGenerator{

		/**
		 * Returns a Weka {@link weka.classifiers.Classifier} that can be used to for training on new data.
		 * @return a Weka {@link weka.classifiers.Classifier} that can be used to for training on new data.
		 */
		Classifier generateClassifier();

	}

}
