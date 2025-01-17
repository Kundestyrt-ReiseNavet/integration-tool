package algorithms.equivalencematching;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.semanticweb.owl.align.Alignment;
import org.semanticweb.owl.align.AlignmentException;
import org.semanticweb.owl.align.AlignmentProcess;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import algorithms.utilities.Sigmoid;
import algorithms.wordembedding.VectorExtractor;
import fr.inrialpes.exmo.align.impl.BasicAlignment;
import fr.inrialpes.exmo.align.impl.BasicConfidence;
import fr.inrialpes.exmo.align.impl.ObjectAlignment;
import fr.inrialpes.exmo.align.impl.URIAlignment;
import fr.inrialpes.exmo.align.impl.rel.A5AlgebraRelation;
import services.interfaces.Algorithm;
import services.settings.AlgorithmSettings;

/**
 * 
 * 
 * The WordEmbeddingMatcher matches concepts from two ontologies based on their associated embedding vectors.
 * @author audunvennesland
 *
 */
public class WordEmbeddingMatcherSigmoid extends ObjectAlignment implements AlignmentProcess, Algorithm {

	//these attributes are used to calculate the weight associated with the matcher's confidence value
	double profileScore;
	int slope;
	double rangeMin;
	double rangeMax;
	OWLOntology sourceOntology;
	OWLOntology targetOntology;
	String vectorFile;
	


	public WordEmbeddingMatcherSigmoid(double profileScore){
		this.profileScore = profileScore;
	}

	public WordEmbeddingMatcherSigmoid(OWLOntology onto1, OWLOntology onto2, String vectorFile, double profileScore, int slope, double rangeMin, double rangeMax) {
		this.profileScore = profileScore;
		this.slope = slope;
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;
		this.sourceOntology = onto1;
		this.targetOntology = onto2;
		this.vectorFile = vectorFile;
	}

	public URIAlignment run(File ontoFile1, File ontoFile2) throws OWLOntologyCreationException, AlignmentException {
		String vectorFile = AlgorithmSettings.VECTORFILE;
		int slope = AlgorithmSettings.SLOPE; 
		double rangeMin = AlgorithmSettings.RANGEMIN; 
		double rangeMax = AlgorithmSettings.RANGEMAX;

		return returnWEMAlignment(ontoFile1, ontoFile2, vectorFile, profileScore, slope, rangeMin, rangeMax);
	}
	
	/**
	 * Returns an alignment holding relations computed by the Word Embedding Matcher (WEM).
	 * @param ontoFile1 source ontology
	 * @param ontoFile2 target ontology
	 * @param vectorFile a file holding terms and corresponding embedding vectors.
	 * @param weight a weight imposed on the confidence value (default 1.0)
	 * @return an URIAlignment holding a set of relations (cells)
	 * @throws OWLOntologyCreationException
	 * @throws AlignmentException
	   Jul 15, 2019
	 */
	public static URIAlignment returnWEMAlignment (File ontoFile1, File ontoFile2, String vectorFile, double profileScore, int slope, double rangeMin, double rangeMax) throws OWLOntologyCreationException, AlignmentException {
		
		URIAlignment WEMAlignment = new URIAlignment();
		
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology onto1 = manager.loadOntologyFromOntologyDocument(ontoFile1);
		OWLOntology onto2 = manager.loadOntologyFromOntologyDocument(ontoFile2);
		
		AlignmentProcess a = new WordEmbeddingMatcherSigmoid(onto1, onto2, vectorFile, profileScore, slope, rangeMin, rangeMax);
				
		a.init(ontoFile1.toURI(), ontoFile2.toURI());
		Properties params = new Properties();
		params.setProperty("", "");
		a.align((Alignment)null, params);	
		BasicAlignment WordEmbeddingMatcherSigmoidAlignment = new BasicAlignment();

		WordEmbeddingMatcherSigmoidAlignment = (BasicAlignment) (a.clone());

		WordEmbeddingMatcherSigmoidAlignment.normalise();
		
		WEMAlignment = WordEmbeddingMatcherSigmoidAlignment.toURIAlignment();
		
//		WEMAlignment.init( onto1.getOntologyID().getOntologyIRI().get().toURI(), onto2.getOntologyID().getOntologyIRI().get().toURI(), A5AlgebraRelation.class, BasicConfidence.class );
		WEMAlignment.init( onto1.getOntologyID().getOntologyIRI().toURI(), onto2.getOntologyID().getOntologyIRI().toURI(), A5AlgebraRelation.class, BasicConfidence.class );
		return WEMAlignment;
		
	}
	
	/**
	 * Creates a map holding a class as key along with an array of vectors as value.
	 * @param onto an OWL ontology
	 * @param vectorFile a file holding terms and corresponding embedding vectors.
	 * @return a Map<String, double[]) representing classes and corresponding embedding vectors.
	 * @throws IOException
	   Jul 15, 2019
	 */
	public static Map<String, double[]> createVectorMap (OWLOntology onto, String vectorFile) throws IOException {
		
		Map<String, double[]> vectors = new HashMap<String, double[]>();
		
		//create the vector map from the source vector file
		Map<String, ArrayList<Double>> vectorMap = VectorExtractor.createVectorMap (new File(vectorFile));
		ArrayList<Double> labelVector = new ArrayList<Double>();		
		
		for (OWLClass cls : onto.getClassesInSignature()) {
			
			if (vectorMap.containsKey(cls.getIRI().getFragment().toLowerCase())) {				
				
				labelVector = VectorExtractor.getLabelVector(cls.getIRI().getFragment(), vectorMap);
								
				double[] labelVectorArray = new double[labelVector.size()];
				for (int i = 0; i < labelVectorArray.length; i++) {
					labelVectorArray[i] = labelVector.get(i);
				}
				
				vectors.put(cls.getIRI().getFragment().toLowerCase(), labelVectorArray);
			}	
		}
		

		return vectors;
		
		
	}

	/**
	 * Creates an alignment holding a set of relations.
	 * The confidence assigned to each relation is computed by the cosine similarity from embedding vectors associated with each concept name.
	 */
	public void align(Alignment alignment, Properties param) throws AlignmentException {
		Map<String, double[]> vectorMapSourceOntology = new HashMap<String, double[]>();
		Map<String, double[]> vectorMapTargetOntology = new HashMap<String, double[]>();
		try {
			vectorMapSourceOntology = createVectorMap(sourceOntology, vectorFile);
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		try {
			vectorMapTargetOntology = createVectorMap(targetOntology, vectorFile);
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		double[] sourceVectors = null;
		double[] targetVectors = null;

		double cosineSim = 0;
		int idCounter = 0;

		
		try {
			// Match classes
			for ( Object sourceObject: ontology1().getClasses() ){
				for ( Object targetObject: ontology2().getClasses() ){

					idCounter++;
					
					String source = ontology1().getEntityName(sourceObject).toLowerCase();
					String target = ontology2().getEntityName(targetObject).toLowerCase();

					if (vectorMapSourceOntology.containsKey(source) && vectorMapTargetOntology.containsKey(target)) {
						
						sourceVectors = vectorMapSourceOntology.get(source);
						targetVectors = vectorMapTargetOntology.get(target);

						//ensure that both vectors have the same, correct size (not sure why they shouldn´t be...)
						if (sourceVectors.length == 300 && targetVectors.length == 300) {

							cosineSim = algorithms.utilities.Cosine.cosineSimilarity(sourceVectors, targetVectors);
							
							//calculate the final confidence using a sigmoid function
							addAlignCell("WordEmbeddingMatcherSigmoid" +idCounter, sourceObject, targetObject, "=", 
									Sigmoid.weightedSigmoid(slope, cosineSim, Sigmoid.transformProfileWeight(profileScore, rangeMin, rangeMax)));

						} else {
							
							addAlignCell("WordEmbeddingMatcherSigmoid" +idCounter, sourceObject, targetObject, "=", 0);

						}


					} else {
						
						addAlignCell("WordEmbeddingMatcherSigmoid" +idCounter, sourceObject, targetObject, "=", 0);
						
					}

				}
			}

		} catch (Exception e) { e.printStackTrace(); }
		



	}


}


