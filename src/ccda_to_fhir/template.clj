(ns ccda-to-fhir.template
  ( :require [clojure.data.zip.xml :as x] 
             [clojure.data.xml :as xml]
             [clojure.zip :as zip]
             [clojure.data.zip :as dz])
  )


(def ids
  {
   :functional-status-section "2.16.840.1.113883.10.20.22.2.14"
   :functional-status-result-organizer "2.16.840.1.113883.10.20.22.4.66" 
   :functional-status-result-observation "2.16.840.1.113883.10.20.22.4.67"
   }
  )

(def definitions
  {

   :functional-status-section
   {
    :inherit :texted
    :fhir-mapping {:path "Composition.section" }
    :props [
            {
             :min 1
             :max 1
             :path "./title"
             :fhir-mapping {:path "Composition.section.title"}
             }
            {
             :required false
             :path "./entry"
             :template :functional-status-result-organizer
             :fhir-mapping {:path "Composition.section.entry" }
             }
            {
             :required false
             :path "./entry"
             :template :functional-status-result-observation
             :fhir-mapping {:path "Composition.section.entry"}
             }
            ]
    }
   
   :functional-status-result-organizer
   {
    :inherit :result-organizer
    :fhir-mapping {:path "Observation" }
    :props [
            {
             :min 1
             :path "./component"
             :template :functional-status-result-observation
             :fhir-mapping
             {
                            :path "Observation.related.target"
                            :details {:Observation.related.type "has-member"}
                            }
             } 
            ]
    }
   :coded
   {
    :props [
            {
             :key :code
             :path "./code"
             :min 1
             :max 1
             }
            ]
    }

   :texted
   {
    :props [
            {
             :key :text
             :path "./text"
             :max 1
             }
            ]
    }


   :fhir-type-Period
   {
    :fhir-mapping {:as :Type :path "Period"}
    :props [
            {
             :path [:low]
             :fhir-mapping {:path "Period.start"}
             }
            {
             :path [:high]
             :fhir-mapping {:path "Period.end"}
             }
            ]
    }

   :fhir-type-CodeableConcept
   {
    :fhir-mapping {:as :Type :path "CodeableConcept"}
    :props [
            {
             :path [(x/attr :displayName)]
             :max 1
             :fhir-mapping {:path "CodeableConcept.display"}
             }
            {
             :path []
             :max 1
             :fhir-mapping {:path "CodeableConcept.coding"
                            :details {"CodeableConcept.coding.primary" true}
                            }
             }
            {
             :path [:translation]
             :fhir-mapping {:path "CodeableConcept.coding"}
             }
            ]
    }

   :fhir-type-Coding
   {
    :fhir-mapping {:as :Type :path "Coding"}
    :props [
            {
             :path [(x/attr :code)]
             :max 1
             :fhir-mapping {:path "Coding.code"}
             }
            {
             :path [(x/attr :displayName)]
             :max 1
             :fhir-mapping {:path "Coding.display"}
             }
            {
             :path [(x/attr :codeSystem)]
             :max 1
             :fhir-mapping {:path "Coding.system" :transform :oid-to-uri}
             }
            ]
    }

   :functional-status-result-observation
   {
    :inherit [:result-observation :coded :texted]
    :fhir-mapping {:as :Resource :path  "Observation" }
    :props [
            {
             :min 1
             :max 1
             :path "./effectiveTime"
             :fhir-mapping {:path "Observation.applies[x]"}
             }
            {
             :min 1
             :max 1
             :path "./value"
             :fhir-mapping {:path "Observation.value[x]"}
             }
            {
             :min 0
             :max 1
             :path "./interpretationCode"
             :fhir-mapping {:path "Observation.interpretation"}
             }
            {
             :min 0
             :max 1
             :path "./methodCode"
             :fhir-mapping {:path "Observation.method"}
             }
            {
             :min 0
             :max 1
             :path "./targetSiteCode"
             :fhir-mapping {:path "Observation.bodySite"}
             }
            ; These are problemantic -- specifically: what do they *mean* ?
            ;   5. MAY contain zero or one [0..1] entryRelationship (CONF:13892) such that it
            ;   a. SHALL contain exactly one [1..1] @typeCode="REFR" refers to (CONF:14596).
            ;   b. SHALL contain exactly one [1..1] Non-Medicinal Supply Activity
            ;   (templateId:2.16.840.1.113883.10.20.22.4.50) (CONF:14218).
            ;   16. MAY contain zero or one [0..1] entryRelationship (CONF:13895) such that it
            ;   a. SHALL contain exactly one [1..1] @typeCode="REFR" refers to (CONF:14597).
            ;   b. SHALL contain exactly one [1..1] Caregiver Characteristics
            ;   (templateId:2.16.840.1.113883.10.20.22.4.72) (CONF:13897).
            ;   17. MAY contain zero or one [0..1] entryRelationship (CONF:14465) such that it
            ;   a. SHALL contain exactly one [1..1] @typeCode="COMP" has component (CONF:14598).
            ;   b. SHALL contain exactly one [1..1] Assessment Scale Observation
            ;   (templateId:2.16.840.1.113883.10.20.22.4.69) (CONF:14466).
            ;   18. SHOULD contain zero or more [0..*] referenceRange (CONF:13937).
            ;   a. The referenceRange, if present, SHALL contain exactly one [1..1] observationRange (CONF:13938)
            ]
    }
   })
