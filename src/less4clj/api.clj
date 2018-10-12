(ns less4clj.api
  (:require [less4clj.watcher :as watcher]
            [less4clj.core :as core]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]))

(defn main-file? [file]
  (.endsWith (.getName file) ".main.less"))

(defn find-main-files [source-paths]
  (mapcat (fn [source-path]
            (let [file (io/file source-path)]
              (->> (file-seq file)
                   (filter main-file?)
                   (map (fn [x] [(.getPath x) (.toString (.relativize (.toURI file) (.toURI x)))])))))
          source-paths))

(defn print-warning [warning]
  (println (format "WARN: %s %s\n" (:message warning)
                   (str (if (:uri (:source warning))
                          (str "on file "
                               (:uri (:source warning))
                               (if (:line warning)
                                 (str " at line " (:line warning) " character " (:char warning)))))))))

(defn compile-less [main-files {:keys [auto target-path] :as options}]
  (try
    (doseq [[path relative-path] main-files]
      (println (format "Compiling {less}... %s" relative-path))
      (let [result
            (try
              (core/less-compile-to-file
                path
                (.getPath (io/file target-path))
                relative-path
                (dissoc options :target-path :source-paths))
              (catch Exception e
                (if auto
                  (println (.getMessage e))
                  (throw e))))]
        (doseq [warning (:warnings result)]
          (print-warning warning))))
    (catch Exception e
      (if (= :less4clj.core/error (:type (ex-data e)))
        (println (.getMessage e))
        (throw e)))))

(s/def ::source-maps (s/coll-of string? :into vec))
(s/def ::auto boolean?)
(s/def ::help boolean?)
(s/def ::target-path string?)
(s/def ::compression boolean?)
(s/def ::source-map boolean?)
(s/def ::inline-javascript boolean?)
(s/def ::verbosity #{1 2})
(s/def ::options (s/keys :req-un [::source-paths ::target-path]
                         :opt-un [::auto ::help ::compression ::source-map
                                  ::inline-javascript ::verbosity]))

(defn build [{:keys [source-paths auto] :as options}]
  (when-not (s/valid? ::options options)
    (s/explain-out (s/explain-data ::options options)))
  (let [main-files (vec (find-main-files source-paths))
        options (dissoc options :source-paths)]
    (if auto
      (watcher/start source-paths (fn [& _] (compile-less main-files options)))
      (compile-less main-files options))))

(defn start [options]
  (build (assoc options :auto true)))

(defn stop [this]
  (if this
    (watcher/stop this)))
