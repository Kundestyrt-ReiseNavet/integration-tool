package services.dataclasses;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLDataPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLPropertyExpression;
import org.semanticweb.owlapi.model.OWLPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.util.DefaultPrefixManager;

import kotlin.NotImplementedError;
import services.IO.OWLOntologyToFile;
import services.parsers.schema.SpreadsheetParser;

public class OntologyConcept {
    // All fields except "name" are optional. Look at schema-org.owl for examples.
    public String name = ""; // rdfs:label
    public String description = ""; // rdfs:comment
    public String subClassof = ""; // F.eks. a AboutPage is a part of a WebPage, a brother is a part of a family (Merk: is a part of)
    public String domain = ""; // F.eks. a truck is a type of car (Merk: is a type of)
    public String range = ""; // F.eks. a car is a thing, a nose is a thing (Merk: is a)
    public Boolean matchThis = true; // Typically you don't want to use the domains/range in the matching

    public static void main(String[] args) throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        SpreadsheetParser parser = new SpreadsheetParser();
        OWLOntology o = OntologyConcept.toOWLOntology(parser.parse("files/temp/GTFS-Flex.xlsx"), m);
        //OWLOntology o = OntologyConcept.test(m);
        OWLOntologyToFile.Convert(o, "files/temp/onto", m);
    }

    public static OWLOntology toOWLOntology(List<OntologyConcept> ontologyConcepts, OWLOntologyManager m)
            throws Exception {
        IRI example_iri = IRI.create("http://www.semanticweb.org/ontologies/ont.owl");
        OWLOntology o = m.createOntology(example_iri);
        OWLDataFactory df = OWLManager.getOWLDataFactory();

        Map<String, OWLClass> nameToOWLClass = new HashMap<String, OWLClass>();
        //Fix label and description
        for(OntologyConcept concept : ontologyConcepts){
            if (nameToOWLClass.containsKey(concept.name)){ //It only uses one of the comments when duplicate entries occours. Good enough?
                continue;
            }
            OWLClass c = df.getOWLClass(IRI.create(example_iri + "#" + concept.name));
            AddLabel(concept.name, c, df, m, o);
            AddDescription(concept.description, c, df, m, o);
            nameToOWLClass.put(concept.name, c);
            if (!concept.domain.equals("") || !concept.range.equals("") || !concept.matchThis){
                throw new NotImplementedError("domain, range and !matchThis are currently not supported");
            }
        }
        //Fix subclasses
        for(OntologyConcept concept : ontologyConcepts){
            if (concept.subClassof.equals("")){
                continue;
            }
            OWLClass c = nameToOWLClass.get(concept.name);
            OWLClass c2 = nameToOWLClass.get(concept.subClassof);
            AddSubClassOf(c, c2, df, m, o);
        }
        return o;
    }
    public static void AddLabel(String name, OWLClass c, OWLDataFactory df, OWLOntologyManager m, OWLOntology o){
        OWLAnnotation labelAnno = df.getOWLAnnotation(df.getRDFSLabel(), df.getOWLLiteral(name));
        OWLAxiom label_ax = df.getOWLAnnotationAssertionAxiom(c.getIRI(), labelAnno);
        m.applyChange(new AddAxiom(o, label_ax));
    }

    public static void AddDescription(String description, OWLClass c, OWLDataFactory df, OWLOntologyManager m, OWLOntology o){
        if (description == ""){
            return;
        }
        OWLAnnotation commentAnno = df.getOWLAnnotation(df.getRDFSComment(),
        df.getOWLLiteral(description, ""));
        OWLAxiom comment_ax = df.getOWLAnnotationAssertionAxiom(c.getIRI(), commentAnno);
        m.applyChange(new AddAxiom(o, comment_ax));
    }

    public static void AddSubClassOf(OWLClass child, OWLClass parent, OWLDataFactory df, OWLOntologyManager m, OWLOntology o){
        if (parent == null){
            System.out.println("Warning: SubClassOf could not find parent class");
            return;
        }
        OWLSubClassOfAxiom sub_ax = df.getOWLSubClassOfAxiom(child, parent);
        m.applyChange(new AddAxiom(o, sub_ax));
    }


    /**
     * Test function for implementing domain, range and dontMatchThis
     */
    public static OWLOntology test(OWLOntologyManager m) throws Exception{
        IRI example_iri = IRI.create("http://www.semanticweb.org/ontologies/ont.owl");
        OWLOntology o = m.createOntology(example_iri);
        OWLDataFactory df = OWLManager.getOWLDataFactory();
        OWLClass baby = df.getOWLClass(IRI.create(example_iri + "#Baby"));
        OWLClass person = df.getOWLClass(IRI.create(example_iri + "#Person"));
        
        //PrefixManager pm= new DefaultPrefixManager("http://www.co-ode.org/ontologies/ont.owl#");
        OWLObjectPropertyExpression man= df.getOWLObjectProperty(baby.getIRI());
        //OWLDatatype dt = df.getOWLDatatype("xsd:string",pm);
        OWLObjectPropertyDomainAxiom domain=df.getOWLObjectPropertyDomainAxiom(man, person);
        //OWLObjectPropertyRangeAxiom range= df.getOWLObjectPropertyRangeAxiom(man, (OWLClassExpression) dt);

        m.applyChange(new AddAxiom(o, domain));
        return o;
    }
}
