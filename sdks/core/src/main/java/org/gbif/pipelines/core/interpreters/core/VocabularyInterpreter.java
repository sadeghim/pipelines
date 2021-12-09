package org.gbif.pipelines.core.interpreters.core;

import static org.gbif.pipelines.core.utils.ModelUtils.extractNullAwareOptValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.var;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.pipelines.core.factory.FileVocabularyFactory;
import org.gbif.pipelines.io.avro.BasicRecord;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.VocabularyConcept;
import org.gbif.vocabulary.lookup.LookupConcept;
import org.gbif.vocabulary.lookup.VocabularyLookup;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VocabularyInterpreter {

  private static final Map<Term, BiConsumer<BasicRecord, VocabularyConcept>> VOCABULARY_FN_MAP =
      new HashMap<>();

  static {
    VOCABULARY_FN_MAP.put(DwcTerm.lifeStage, BasicRecord::setLifeStage);
    VOCABULARY_FN_MAP.put(DwcTerm.establishmentMeans, BasicRecord::setEstablishmentMeans);
    VOCABULARY_FN_MAP.put(DwcTerm.degreeOfEstablishment, BasicRecord::setDegreeOfEstablishment);
    VOCABULARY_FN_MAP.put(DwcTerm.pathway, BasicRecord::setPathway);
  }

  /** {@link DwcTerm#lifeStage} interpretation. */
  public static BiConsumer<ExtendedRecord, BasicRecord> interpretLifeStage(
      FileVocabularyFactory fileVocabularyFactory) {
    return interpretVocabulary(fileVocabularyFactory, DwcTerm.lifeStage);
  }

  /** {@link DwcTerm#establishmentMeans} interpretation. */
  public static BiConsumer<ExtendedRecord, BasicRecord> interpretEstablishmentMeans(
      FileVocabularyFactory fileVocabularyFactory) {
    return interpretVocabulary(fileVocabularyFactory, DwcTerm.establishmentMeans);
  }

  /** {@link DwcTerm#degreeOfEstablishment} interpretation. */
  public static BiConsumer<ExtendedRecord, BasicRecord> interpretDegreeOfEstablishment(
      FileVocabularyFactory fileVocabularyFactory) {
    return interpretVocabulary(fileVocabularyFactory, DwcTerm.degreeOfEstablishment);
  }

  /** {@link DwcTerm#pathway} interpretation. */
  public static BiConsumer<ExtendedRecord, BasicRecord> interpretPathway(
      FileVocabularyFactory fileVocabularyFactory) {
    return interpretVocabulary(fileVocabularyFactory, DwcTerm.pathway);
  }

  /**
   * Extracts the value of vocabulary concept and set
   *
   * @param br record to be modified
   * @param term to lookup and set
   * @param c to extract the value from
   */
  protected static void setLookupConcept(BasicRecord br, Term term, LookupConcept c) {

    var vocabularyConsumer = VOCABULARY_FN_MAP.get(term);

    if (vocabularyConsumer != null) {
      // we sort the parents starting from the top as in taxonomy
      List<String> parents = new ArrayList<>(c.getParents());
      Collections.reverse(parents);

      // add the concept itself
      parents.add(c.getConcept().getName());

      VocabularyConcept concept =
          VocabularyConcept.newBuilder()
              .setConcept(c.getConcept().getName())
              .setLineage(parents)
              .build();

      vocabularyConsumer.accept(br, concept);
    } else {
      throw new IllegalArgumentException("Term {} " + term + " not handled as vocabulary");
    }
  }

  /** {@link DwcTerm#lifeStage} interpretation. */
  private static BiConsumer<ExtendedRecord, BasicRecord> interpretVocabulary(
      FileVocabularyFactory fileVocabularyFactory, Term term) {
    return (er, br) -> {
      if (fileVocabularyFactory != null) {
        VocabularyLookup vocabularyLookup = fileVocabularyFactory.getVocabularyLookup(term);

        if (vocabularyLookup != null) {
          extractNullAwareOptValue(er, term)
              .flatMap(vocabularyLookup::lookup)
              .ifPresent(c -> setLookupConcept(br, term, c));
        }
      }
    };
  }
}
