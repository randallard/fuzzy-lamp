(ns app.client
  (:require
    [com.fulcrologic.fulcro.react.version18 :refer [with-react18]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]))

(def new-board {:board/id 1
                :board/size 3
                :board/plans [{:plan/id 1
                               :plan/number 1
                               :plan/steps [{:step/id 1
                                             :step/number 1
                                             :step/blocks []
                                             :step/positions [{:position/id 1
                                                             :position/row-number 1
                                                             :position/space-number 2}]}]}]
                :board/rows [{:row/id 4
                              :row/number 4
                              :row/type :row-type/goal}
                             {:row/id 3
                              :row/number 3
                              :row/type :row-type/spaces
                              :row/spaces [{:space/id 7
                                            :space/number 1
                                            :space/status :free
                                            :space/occupied nil}
                                           {:space/id 8
                                            :space/number 2
                                            :space/status :free
                                            :space/occupied nil}
                                           {:space/id 9
                                            :space/number 3
                                            :space/status :free
                                            :space/occupied nil}]}
                             {:row/id 2
                              :row/number 2
                              :row/type :row-type/spaces
                              :row/spaces [{:space/id 4
                                            :space/number 1
                                            :space/status :free
                                            :space/occupied nil}
                                           {:space/id 5
                                            :space/number 2
                                            :space/status :free
                                            :space/occupied nil}
                                           {:space/id 6
                                            :space/number 3
                                            :space/status :free
                                            :space/occupied nil}]}
                             {:row/id 1
                              :row/number 1
                              :row/type :row-type/spaces
                              :row/spaces [{:space/id 1
                                            :space/number 1
                                            :space/status :free
                                            :space/occupied nil}
                                           {:space/id 2
                                            :space/number 2
                                            :space/status :free
                                            :space/occupied [{:occupied/player :us
                                                              :occupied/steps [1]}]}
                                           {:space/id 3
                                            :space/number 3
                                            :space/status :free
                                            :space/occupied nil}]}]})
(defn space-css [type occupied]
  {:className (str "space "
                (if (not (nil? occupied)) "occupied ")
                   (if (= type :row-type/goal) "goal "))})
(defsc Space [this {:space/keys [id number status occupied]}]
  {}
  (dom/span (space-css nil occupied) "Space[" number "]"))
(def ui-space (comp/factory Space {:keyfn :space/id}))

(defsc Row [this {:row/keys [id number type] :as props}]
  {:query [:row/id :row/number :row/type]
   :ident :row/id}
  (dom/div (str "Row[" number "] type " type)
           (dom/div {:style {:padding "15px"}}
                    (cond
                      (= type :row-type/goal) (dom/span (space-css type nil) "GOAL")
                      #_#_(= type :row-type/spaces) (map ui-space spaces)))))
(def ui-row (comp/factory Row {:keyfn :row/id}))
(defsc Position [this {:position/keys [id row-number space-number]}]
  {}
  (dom/span "player at ( row " row-number ", column " space-number " )"))
(def ui-position (comp/factory Position {:keyfn :position/id}))
(defsc Block [this {:block/keys [id row-number space-number]}]
  {}
  (dom/span "block set at ( row " row-number ", column " space-number " )"))
(def ui-block (comp/factory Block {:keyfn :block/id}))
(defsc Step [this {:step/keys [id number positions blocks]}]
  {}
  (dom/div "Step " number " "
           (map ui-position positions)
           (map ui-block blocks)))
(def ui-step (comp/factory Step {:keyfn :step/id}))
(defsc Plan [this {:plan/keys [id number steps] :as props}]
  {}
  (dom/div (dom/p "Plan Number " number
                  (dom/ul (map ui-step steps)))))
(def ui-plan (comp/factory Plan {:keyfn :plan/id}))
(defsc Board [this {:board/keys [id size rows] :as props}]
  {:query [:board/id :board/size {:board/rows (comp/get-query Row)}]
   :ident :board/id }
  (dom/div {:style {:width "100%"}}
    (dom/div {:style {:float "left" :padding "10px"}}
           (dom/h2 "Board [" id "] Size " size)
           (dom/div (map ui-row rows)))
    #_(dom/div {:style {:float "left" :padding "10px"}}
           (dom/h2 "Plans")
           (dom/div (map ui-plan plans)))))

(def ui-board (comp/factory Board {:keyfn :board/id}))

(defonce app (-> (app/fulcro-app) (with-react18)))

(defsc Root [this {:root/keys [board]}]
  {:query [{:root/board (comp/get-query Board)}]}
  (dom/div (ui-board board)))

(defn ^:export init
  "Shadow-cljs sets this up to be our entry-point function. See shadow-cljs.edn `:init-fn` in the modules of the main build."
  []
  (app/mount! app Root "app")
  (js/console.log "Loaded"))

(defn ^:export refresh
  "During development, shadow-cljs will call this on every hot reload of source. See shadow-cljs.edn"
  []
  ;; re-mounting will cause forced UI refresh, update internals, etc.
  (app/mount! app Root "app")
  ;; As of Fulcro 3.3.0, this addition will help with stale queries when using dynamic routing:
  (comp/refresh-dynamic-queries! app)
  (js/console.log "Hot reload"))

(comment
  (reset! (::app/state-atom app) {})
  (merge/merge-component! app Board new-board
                          :replace [:root/board])

  (merge/merge-component! app Board {:board/id 2
                                     :board/size 3
                                     :board/rows [{:row/id 4
                                                   :row/number 4
                                                   :row/type :row-type/goal}]}
                          :replace [:root/board])
  (app/current-state app)
  (app/schedule-render! app)

  )