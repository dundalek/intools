(ns reconsierge.main
  (:import (org.graalvm.polyglot Context Source Context$Builder Source$Builder Value))
  (:gen-class))

(set! *warn-on-reflection* true)

(def ^Context$Builder context-builder
  (doto (Context/newBuilder (into-array String ["js"]))
     (.option "js.timer-resolution" "1")
     (.option "js.java-package-globals" "false")
     (.out System/out)
     (.err System/err)
     (.allowAllAccess true)
     (.allowNativeAccess true)))

(def ^Context context (.build context-builder))

(defn ^Value execute-fn [^Context context fn & args]
  (let [fn-ref (.eval context "js" fn)
        args (into-array Object args)]
    (assert (.canExecute fn-ref) (str "cannot execute " fn))
    (.execute fn-ref args)))

; (def ^java.io.File app-js (clojure.java.io/file "out/main.js"))

(def ^java.io.File app-js (clojure.java.io/file "pom.js"))

(def app-source (.build (Source/newBuilder "js" app-js)))
(.eval context app-source)

(defn -main [& _args]

  (let [^Value result (execute-fn context "myfn" "Polyglot Graal 🌈")]
    (println (.asString result)))

  #_(let [^Value result (execute-fn context "my.app.html" "Polyglot Graal 🌈")]
      (println (.asString result)))

  #_(println "XX Hello world!"))
