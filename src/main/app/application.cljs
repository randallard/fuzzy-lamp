(ns app.application
  (:require
    [com.fulcrologic.fulcro.react.version18 :refer [with-react18]]
    [com.fulcrologic.fulcro.application :as app]))
(defonce app (-> (app/fulcro-app) (with-react18)))