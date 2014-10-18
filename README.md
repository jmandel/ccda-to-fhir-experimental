# ccda-to-fhir

Experimental mapping tool to support C-CDA -> FHIR conversion and QA.


## To run

1. Install [leiningen](http://leiningen.org/)
2. `lein run -m ccda-to-fhir.test`


## TODO

- [x] Flesh out `ccda-context` as an atom that includes `:Patient` and `:resources` that get built up.
- [x] Update the property-merging algorithm to support References.
- [ ] Map "system" from OID -> URIs in Codings
- [ ] Map from "simple" C-CDA codes like "M" for gender --> FHIR Codings
- [ ] Final output step that serialized the bundle to JSON
- [ ] Special handling of context in the bundle (with a fixed key, e.g. "cid:context-patient")
