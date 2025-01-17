package algorithms.subsumptionmatching;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

import algorithms.mismatchdetection.ConfirmSubsumption;
import algorithms.utilities.OntologyOperations;
import algorithms.utilities.Sigmoid;
import algorithms.utilities.StringUtilities;
import fr.inrialpes.exmo.align.impl.BasicAlignment;
import fr.inrialpes.exmo.align.impl.BasicConfidence;
import fr.inrialpes.exmo.align.impl.ObjectAlignment;
import fr.inrialpes.exmo.align.impl.URIAlignment;
import fr.inrialpes.exmo.align.impl.rel.A5AlgebraRelation;
import rita.wordnet.jwnl.JWNLException;
import services.interfaces.Algorithm;
import services.settings.AlgorithmSettings;

public class DefinitionSubsumptionMatcherSigmoid extends ObjectAlignment implements AlignmentProcess, Algorithm {

	OWLOntology sourceOntology;
	OWLOntology targetOntology;

	//these attributes are used to calculate the weight associated with the matcher's confidence value
	double profileScore;
	int slope;
	double rangeMin;
	double rangeMax;

	public DefinitionSubsumptionMatcherSigmoid(double profileScore){
		this.profileScore = profileScore;
	}
	
	public DefinitionSubsumptionMatcherSigmoid(OWLOntology onto1, OWLOntology onto2, double profileScore, int slope, double rangeMin, double rangeMax) {
		this.sourceOntology = onto1;
		this.targetOntology= onto2;
		this.profileScore = profileScore;
		this.slope = slope;
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;

	}

	public URIAlignment run(File ontoFile1, File ontoFile2) throws OWLOntologyCreationException, AlignmentException {
		int slope = AlgorithmSettings.SLOPE; 
		double rangeMin = AlgorithmSettings.RANGEMIN; 
		double rangeMax = AlgorithmSettings.RANGEMAX;

		return returnDSMAlignment(ontoFile1, ontoFile2, profileScore, slope, rangeMin, rangeMax); 
	}

	public static URIAlignment returnDSMAlignment (File ontoFile1, File ontoFile2, double profileScore, int slope, double rangeMin, double rangeMax) throws OWLOntologyCreationException, AlignmentException {

		URIAlignment DSMAlignment = new URIAlignment();

		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology onto1 = manager.loadOntologyFromOntologyDocument(ontoFile1);
		OWLOntology onto2 = manager.loadOntologyFromOntologyDocument(ontoFile2);

		AlignmentProcess a = new DefinitionSubsumptionMatcherSigmoid(onto1, onto2, profileScore, slope, rangeMin, rangeMax);
		a.init(ontoFile1.toURI(), ontoFile2.toURI());
		Properties params = new Properties();
		params.setProperty("", "");
		a.align((Alignment)null, params);	
		BasicAlignment DefinitionSubsumptionMatcherSigmoidAlignment = new BasicAlignment();

		DefinitionSubsumptionMatcherSigmoidAlignment = (BasicAlignment) (a.clone());

		DSMAlignment = DefinitionSubsumptionMatcherSigmoidAlignment.toURIAlignment();

		DSMAlignment.init( onto1.getOntologyID().getOntologyIRI().toURI(), onto2.getOntologyID().getOntologyIRI().toURI(), A5AlgebraRelation.class, BasicConfidence.class );

		return DSMAlignment;

	}

	public void align(Alignment alignment, Properties param) throws AlignmentException {
		Map<String, List<String>> defMapSource = new HashMap<String, List<String>>();
		Map<String, List<String>> defMapTarget = new HashMap<String, List<String>>();

		//lexico-syntactic patterns
		List<String> patterns = new ArrayList<String>();
		patterns.add("including");
		patterns.add("includes");
		patterns.add("e.g.");
		patterns.add("such as");
		patterns.add("for example");

		try {
			defMapSource = getDefMapTokens(sourceOntology, patterns);
		} catch (JWNLException | IOException e1) {
			e1.printStackTrace();
		}
		try {
			defMapTarget = getDefMapTokens(targetOntology, patterns);
		} catch (JWNLException | IOException e1) {
			e1.printStackTrace();
		}


		List<String> sourceDefinition = null;
		List<String> targetDefinition = null;

		int idCounter = 0; 

		try {
			// Match classes
			for ( Object sourceObject: ontology1().getClasses() ) {
				for ( Object targetObject: ontology2().getClasses() ){

					idCounter++;

					String source = ontology1().getEntityName(sourceObject).toLowerCase();
					String target = ontology2().getEntityName(targetObject).toLowerCase();

					System.out.println("source is " + source + " and target is " + target);

					if (defMapSource.containsKey(source)) {

						sourceDefinition = defMapSource.get(source);

						System.out.println("sourceDefinition contains " + sourceDefinition);

						//iterate all sourceDefinition tokens and check if they match the target concept and if these are from the same domain
						for (String s : sourceDefinition) {

							if (s.equalsIgnoreCase(target)) {

								//if any of the compounds in source and target are from the same domain AND they´re not meronyms we return a confidence of 1.0
								if (ConfirmSubsumption.conceptsFromSameDomain(ontology1().getEntityName(sourceObject), ontology2().getEntityName(targetObject)) 
										&& !ConfirmSubsumption.isMeronym(ontology1().getEntityName(sourceObject), ontology2().getEntityName(targetObject))) {
									addAlignCell("DefinitionSubsumptionMatcherSigmoid" +idCounter, sourceObject, targetObject, "&gt;", 
											Sigmoid.weightedSigmoid(slope, 1.0, Sigmoid.transformProfileWeight(profileScore, rangeMin, rangeMax)));
									//else we return a confidence of 0.75
								} else if (ConfirmSubsumption.conceptsFromSameDomain(ontology1().getEntityName(sourceObject), ontology2().getEntityName(targetObject)) 
										|| !ConfirmSubsumption.isMeronym(ontology1().getEntityName(sourceObject), ontology2().getEntityName(targetObject))) {
									addAlignCell("DefinitionSubsumptionMatcherSigmoid" +idCounter, sourceObject, targetObject, "&gt;", 
											Sigmoid.weightedSigmoid(slope, 0.75, Sigmoid.transformProfileWeight(profileScore, rangeMin, rangeMax)));

								} 

							} else {
								addAlignCell("DefinitionSubsumptionMatcher" +idCounter + "_" + profileScore + "_", sourceObject, targetObject, "!", 0.0);
							}

						}


					} else if (defMapTarget.containsKey(target)) {

						targetDefinition = defMapTarget.get(target);

						//iterate all targetDefinition tokens and check if they match the source concept and if these are from the same domain
						for (String t : targetDefinition) {

							if (t.equalsIgnoreCase(source)) {

								//if any of the compounds in source and target are from the same domain according to WNDomain, we return a confidence of 1.0
								if (ConfirmSubsumption.conceptsFromSameDomain(ontology1().getEntityName(sourceObject), ontology2().getEntityName(targetObject))
										&& !ConfirmSubsumption.isMeronym(ontology1().getEntityName(sourceObject), ontology2().getEntityName(targetObject))) {	

									addAlignCell("DefinitionSubsumptionMatcherSigmoid" +idCounter, sourceObject, targetObject, "&lt;", 
											Sigmoid.weightedSigmoid(slope, 1.0, Sigmoid.transformProfileWeight(profileScore, rangeMin, rangeMax)));

								} else if (ConfirmSubsumption.conceptsFromSameDomain(ontology1().getEntityName(sourceObject), ontology2().getEntityName(targetObject))
										|| !ConfirmSubsumption.isMeronym(ontology1().getEntityName(sourceObject), ontology2().getEntityName(targetObject))) {

									addAlignCell("DefinitionSubsumptionMatcherSigmoid" +idCounter, sourceObject, targetObject, "&lt;", 
											Sigmoid.weightedSigmoid(slope, 0.75, Sigmoid.transformProfileWeight(profileScore, rangeMin, rangeMax)));
								}

							}

							else {
								addAlignCell("DefinitionSubsumptionMatcher" +idCounter + "_" + profileScore + "_", sourceObject, targetObject, "!", 0.0);
							}
						}


					} else {
						addAlignCell("DefinitionSubsumptionMatcher" +idCounter + "_" + profileScore + "_", sourceObject, targetObject, "!", 0.0);
					}


				}

			}

		} catch (Exception e) { e.printStackTrace(); }

	}


	private static Map<String, List<String>> getDefMapTokens(OWLOntology onto, List<String> patterns) throws JWNLException, IOException {

		Map<String, List<String>> defMap = new HashMap<String, List<String>>();
		String extract = null;
		String def = null;
		String cut = null;
		String refined = null;


		for (String pattern : patterns) {

			for (OWLClass c : onto.getClassesInSignature()) {

				def = OntologyOperations.getClassDefinitionFull(onto, c).toLowerCase();

				//include only those definitions that contain lexico-syntactic patterns
				if (def.contains(pattern)) {
					extract = def.substring(def.indexOf(pattern)+pattern.length(), def.length());
					if (extract.contains(".")) { //include only current sentence if more than one sentence in definition.
						cut = extract.substring(0, extract.indexOf("."));
						refined = removeStopWordsAndDigits(cut);
					} else {

						refined = removeStopWordsAndDigits(extract);
					}				

					List<String> tokens = StringUtilities.tokenizeAndLemmatizeToList(refined, true);				

					defMap.put(c.getIRI().getFragment().toLowerCase(), tokens);
				}
			}
		}

			return defMap;

		}



		private static String removeStopWordsAndDigits (String inputString) {

			List<String> stopWordsList = Arrays.asList(
					"a", "an", "and", "are", "as", "at", "be", "but", "by",
					"etc", "for", "if", "in", "into", "is", "it",
					"no", "not", "of", "on", "or", "such",
					"that", "the", "their", "then", "there", "these",
					"they", "this", "to", "was", "will", "with"
					);

			String[] words = inputString.split(" ");
			ArrayList<String> wordsList = new ArrayList<String>();
			StringBuffer sb = new StringBuffer();

			for(String word : words)
			{
				String wordCompare = word.toLowerCase();
				if(!stopWordsList.contains(wordCompare))
				{
					wordsList.add(word);
				}
			}

			for (String str : wordsList){
				sb.append(str + " ");
			}

			return sb.toString().replaceAll("[0-9]","").replaceAll("\\s{2,}", " ").trim();

		}

	}