(ns app.application
  (:require
    [com.fulcrologic.fulcro.react.version18 :refer [with-react18]]
    [com.fulcrologic.fulcro.application :as app]))

(defonce app (-> (app/fulcro-app) (with-react18)))


(comment
  (swap! (::app/state-atom app) assoc-in [:occupied-step/id 1 :occupied-step/steps] [1 2])
  (app/current-state app)
  )