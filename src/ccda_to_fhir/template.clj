(ns ccda-to-fhir.template
  ( :require [clojure.data.zip.xml :as x] 
             [clojure.data.xml :as xml]
             [clojure.zip :as zip]
             [clojure.data.zip :as dz])
  )


(def oids
  {
   :functional-status-section "2.16.840.1.113883.10.20.22.2.14"
   :functional-status-result-organizer "2.16.840.1.113883.10.20.22.4.66" 
   :functional-status-result-observation "2.16.840.1.113883.10.20.22.4.67"
   }
  )

(def definitions
  {

   :ccda-sections
   {
    :props [
            {
             :xpath "./component/structuredBody/component/section"
             :fhir-mapping {:path "Composition.section"}
             :template :functional-status-section
             }
            ]
    }

   :functional-status-section
   {
    :inherit :texted
    :fhir-mapping {:path "Composition.section" }
    :props [
            {
             :min 1
             :max 1
             :xpath "./:b extitle"
             :fhir-mapping {:path "Composition.section.title"}
             }
            {
             :required false
             :xpath "./entry/organizer"
             :template :functional-status-result-organizer
             :fhir-mapping {:path "Composition.section.entry" }
             }
            {
             :required false
             :xpath "./entry/observation"
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
             :xpath "./component/observation"
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
             :xpath "./code"
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
             :xpath "./text"
             :max 1
             }
            ]
    }


   :fhir-type-Period
   {
    :fhir-mapping {:as :Type :path "Period"}
    :props [
            {
             :xpath "./low/@value"
             :fhir-mapping {:path "Period.start"}
             }
            {
             :xpath "./high/@value"
             :fhir-mapping {:path "Period.end"}
             }
            ]
    }

   :fhir-type-CodeableConcept
   {
    :fhir-mapping {:as :Type :path "CodeableConcept"}
    :props [
            {
             :xpath "./@displayName"
             :max 1
             :fhir-mapping {:path "CodeableConcept.text"}
             }
            {
             :xpath "./"
             :max 1
             :fhir-mapping {:path "CodeableConcept.coding"
                            :details {"CodeableConcept.coding.primary" true}
                            }
             }
            {
             :xpath "./translation"
             :fhir-mapping {:path "CodeableConcept.coding"}
             }
            ]
    }

   :fhir-type-Quantity
   {
    :fhir-mapping {:as :Type :path "Quantity"}
    }

   :fhir-type-Attachment
   {
    :fhir-mapping {:as :Type :path "Attachment"}
    }

   :fhir-type-Ratio
   {
    :fhir-mapping {:as :Type :path "Ratio"}
    }

   :fhir-type-SampledData
   {
    :fhir-mapping {:as :Type :path "SampledData"}
    }




  
   :fhir-type-Coding
   {
    :fhir-mapping {:as :Type :path "Coding"}
    :props [
            {
             :xpath "./@code"
             :max 1
             :fhir-mapping {:path "Coding.code"}
             }
            {
             :xpath "./@displayName"
             :max 1
             :fhir-mapping {:path "Coding.display"}
             }
            {
             :xpath "./@codeSystem"
             :max 1
             :fhir-mapping {:path "Coding.system" :transform :oid-to-uri}
             }
            ]
    }

   :ccda-value-element
   {
    "CodeableConcept"
            {
             :props [ {
                     :xpath "./[@xsi:type='CD']"
                     :fhir-mapping {:path "CodeableConcept"}}]
             }
     "string"
            {
             :props [ {
                     :xpath "./[@xsi:type='ST']"
                     :fhir-mapping {:path "string"}}]
             }
    }

   :functional-status-result-observation
   {
    :inherit [:result-observation :coded :texted]
    :fhir-mapping {:as :Resource :path  "Observation" }
    :props [
            {
             :min 1
             :max 1
             :xpath "./effectiveTime"
             :fhir-mapping {:path "Observation.applies[x]"}
             }
            {
             :min 1
             :max 1
             :xpath "./value"
             :fhir-mapping {:path "Observation.value[x]"}
             :template-set :ccda-value-element
             }
            {
             :min 0
             :max 1
             :xpath "./interpretationCode"
             :fhir-mapping {:path "Observation.interpretation"}
             }
            {
             :min 0
             :max 1
             :xpath "./methodCode"
             :fhir-mapping {:path "Observation.method"}
             }
            {
             :min 0
             :max 1
             :xpath "./targetSiteCode"
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
