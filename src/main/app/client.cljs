(ns app.client
  (:require
    [com.fulcrologic.fulcro.react.version18 :refer [with-react18]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]))

(defmutation make-blocked [{:space/keys [id]}]
             (action [{:keys [state]}]
                     (swap! state assoc-in [:space/id id :space/status] :blocked)))
(defmutation make-occupied [{:space/keys [id]}]
  (action [{:keys [state]}]
          (swap! state assoc-in [:space/id id :space/status] :occupied)))
(defn space-css [type space-status]
  {:className (str "space "
                   (if (= space-status :occupied) "occupied ")
                   (if (= space-status :blocked) "blocked ")
                   (if (= type :row-type/goal) "goal "))})
(defsc Occupied [this {:occupied/keys [id player steps]}]
  {:query [:occupied/id :occupied/player :occupied/steps]
   :ident :occupied/id
   :initial-state {:occupied/id :param/id
                   :occupied/player :param/player
                   :occupied/steps :param/steps}}
  (dom/span {} (str "By " player " at " steps)))
(def ui-occupied (comp/factory Occupied {:keyfn :occupied/id}))
(defsc Space [this {:space/keys [id number status occupied]}]
  {:query [:space/id :space/number :space/status {:space/occupied (comp/get-query Occupied)}]
   :ident :space/id
   :initial-state (fn [{:keys [id number status]}]
                    (cond (= status :occupied)
                          {:space/id id
                           :space/number number
                           :space/status status
                           :space/occupied [(comp/get-initial-state Occupied {:id 1
                                                                              :player :us
                                                                              :steps [1]})]}
                          :else {:space/id id
                                 :space/number number
                                 :space/status status
                                 :space/occupied []}))}
  #_(dom/span (space-css nil status) "Space id " id " number " number " "
            (str status " ") (map ui-occupied occupied) )
  (dom/span (space-css nil status)
            (dom/button {:onClick #(comp/transact! this [(make-blocked {:space/id id})]) :style {:margin "0px 15px"}}
                        " block ")
            (dom/button {:onClick #(comp/transact! this [(make-occupied {:space/id id})]) :style {:margin "0px 15px"}}
                        " move ")))
(def ui-space (comp/factory Space {:keyfn :space/id}))

(defsc Row [this {:row/keys [id number type spaces] :as props}]
  {:query [:row/id :row/number :row/type {:row/spaces (comp/get-query Space)}]
   :ident :row/id
   :initial-state (fn [{:keys [id number type]}]
                    (cond (= type :row-type/spaces)
                          {:row/id id
                           :row/number number
                           :row/type type
                           :row/spaces (cond (= number 1) [(comp/get-initial-state Space {:id 1
                                                                                          :number 1
                                                                                          :status :free})
                                                           (comp/get-initial-state Space {:id 2
                                                                                          :number 2
                                                                                          :status :occupied})
                                                           (comp/get-initial-state Space {:id 3
                                                                                          :number 3
                                                                                          :status :free})]
                                             (= number 2) [(comp/get-initial-state Space {:id 4
                                                                                          :number 1
                                                                                          :status :free})
                                                           (comp/get-initial-state Space {:id 5
                                                                                          :number 2
                                                                                          :status :free})
                                                           (comp/get-initial-state Space {:id 6
                                                                                          :number 3
                                                                                          :status :free})]
                                             (= number 3) [(comp/get-initial-state Space {:id 7
                                                                                          :number 1
                                                                                          :status :free})
                                                           (comp/get-initial-state Space {:id 8
                                                                                          :number 2
                                                                                          :status :free})
                                                           (comp/get-initial-state Space {:id 9
                                                                                          :number 3
                                                                                          :status :free})])}
                          :else {:row/id id
                                 :row/number number
                                 :row/type type
                                 :row/spaces [(comp/get-initial-state Space {:id 10
                                                                             :number 1
                                                                             :status :free})]}))}
  (dom/div {} #_(str "Row[" number "] type " type)
           (dom/div {:style {:padding "5px"}}
                    (cond
                      (= type :row-type/goal) (dom/span "GOAL--->GOAL--->GOAL-->" (map ui-space spaces) "<---GOAL<---GOAL---GOAL")
                      (= type :row-type/spaces) (map ui-space spaces)))))

(def ui-row (comp/factory Row {:keyfn :row/id}))
(defsc Position [this {:position/keys [id row-number space-number]}]
  {:query [:position/id :position/row-number :position/space-number]
   :ident :position/id}
  (dom/span {} "player at ( row " row-number ", column " space-number " )"))
(def ui-position (comp/factory Position {:keyfn :position/id}))
(defsc Block [this {:block/keys [id row-number space-number]}]
  {:query [:block/id :block/row-number :block/space-number]
   :ident :block/id}
  (dom/span {} "block set at ( row " row-number ", column " space-number " )"))
(def ui-block (comp/factory Block {:keyfn :block/id}))
(defsc Step [this {:step/keys [id number positions blocks]}]
  {:query [:step/id :step/number {:step/positions (comp/get-query Position)}
           {:step/blocks (comp/get-query Block)}]
   :ident :step/id}
  (dom/div {} "Step " number " "
           (map ui-position positions)
           (map ui-block blocks)))
(def ui-step (comp/factory Step {:keyfn :step/id}))
(defsc Plan [this {:plan/keys [id number steps] :as props}]
  {:query [:plan/id :plan/number {:plan/steps (comp/get-query Step)}]
   :ident :plan/id}
  (dom/div {} (dom/p {} "Plan Number " number
                  (dom/ul {} (map ui-step steps)))))
(def ui-plan (comp/factory Plan {:keyfn :plan/id}))
(defsc Board [this {:board/keys [id size rows plans] :as props}]
  {:query [:board/id :board/size {:board/rows (comp/get-query Row)} {:board/plans (comp/get-query Plan)}]
   :ident :board/id
   :initial-state {:board/id :param/id
                   :board/size :param/size
                   :board/rows [{:id 4
                                 :number 4
                                 :type :row-type/goal}
                                {:id 3
                                 :number 3
                                 :type :row-type/spaces}
                                {:id 2
                                 :number 2
                                 :type :row-type/spaces}
                                {:id 1
                                 :number 1
                                 :type :row-type/spaces}]
                   :board/plans []}}
  (dom/div {:style {:width "100%"}}
    (dom/div {:style {:float "left" :padding "10px"}}
           (dom/h2 {} "Board [" id "] Size " size)
           (dom/div {} (map ui-row rows)))
    (dom/div {:style {:float "left" :padding "10px"}}
           (dom/h2 {} "Plans")
           (dom/div {} (map ui-plan plans)))))

(def ui-board (comp/factory Board {:keyfn :board/id}))

(defonce app (-> (app/fulcro-app) (with-react18)))

(defsc Root [this {:root/keys [board]}]
  {:query [{:root/board (comp/get-query Board)}]
   :initial-state {:root/board {:id 1 :size 3}}}
  (dom/div {} (ui-board board)))

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

(def test-board {:board/id 1
                 :board/size 3
                 :board/plans [{:plan/id 1
                                :plan/number 1
                                :plan/steps [{:step/id 1
                                              :step/number 1
                                              :step/blocks []
                                              :step/positions [{:position/id 1
                                                                :position/row-number 1
                                                                :position/space-number 2}]}
                                             {:step/id 2
                                              :step/number 2
                                              :step/blocks [{:block/id 1
                                                             :block/row-number 1
                                                             :block/space-number 1}]
                                              :step/positions []}]}]
                 :board/rows [{:row/id 4
                               :row/number 4
                               :row/type :row-type/goal}
                              {:row/id 3
                               :row/number 3
                               :row/type :row-type/spaces
                               :row/spaces [{:space/id 7
                                             :space/number 1
                                             :space/status :free
                                             :space/occupied []}
                                            {:space/id 8
                                             :space/number 2
                                             :space/status :free
                                             :space/occupied []}
                                            {:space/id 9
                                             :space/number 3
                                             :space/status :free
                                             :space/occupied []}]}
                              {:row/id 2
                               :row/number 2
                               :row/type :row-type/spaces
                               :row/spaces [{:space/id 4
                                             :space/number 1
                                             :space/status :free
                                             :space/occupied []}
                                            {:space/id 5
                                             :space/number 2
                                             :space/status :free
                                             :space/occupied []}
                                            {:space/id 6
                                             :space/number 3
                                             :space/status :free
                                             :space/occupied []}]}
                              {:row/id 1
                               :row/number 1
                               :row/type :row-type/spaces
                               :row/spaces [{:space/id 1
                                             :space/number 1
                                             :space/status :blocked
                                             :space/occupied [{:occupied/id 2
                                                               :occupied/player :us
                                                               :occupied/steps [2]}]}
                                            {:space/id 2
                                             :space/number 2
                                             :space/status :occupied
                                             :space/occupied [{:occupied/id 1
                                                               :occupied/player :us
                                                               :occupied/steps [1 2]}]}
                                            {:space/id 3
                                             :space/number 3
                                             :space/status :free
                                             :space/occupied []}]}]})
(def new-board {:board/id 1
                :board/size 3
                :board/plans [{:plan/id 1
                               :plan/number 1
                               :plan/steps [{:step/id 1
                                             :step/number 1
                                             :step/blocks []
                                             :step/positions [{:position/id 1
                                                               :position/row-number 1
                                                               :position/space-number 2}]}
                                            {:step/id 2
                                             :step/number 2
                                             :step/blocks [{:block/id 1
                                                            :block/row-number 1
                                                            :block/space-number 1}]
                                             :step/positions []}]}]
                :board/rows [{:row/id 4
                              :row/number 4
                              :row/type :row-type/goal}
                             {:row/id 3
                              :row/number 3
                              :row/type :row-type/spaces
                              :row/spaces [{:space/id 7
                                            :space/number 1
                                            :space/status :free
                                            :space/occupied []}
                                           {:space/id 8
                                            :space/number 2
                                            :space/status :free
                                            :space/occupied []}
                                           {:space/id 9
                                            :space/number 3
                                            :space/status :free
                                            :space/occupied []}]}
                             {:row/id 2
                              :row/number 2
                              :row/type :row-type/spaces
                              :row/spaces [{:space/id 4
                                            :space/number 1
                                            :space/status :free
                                            :space/occupied []}
                                           {:space/id 5
                                            :space/number 2
                                            :space/status :free
                                            :space/occupied []}
                                           {:space/id 6
                                            :space/number 3
                                            :space/status :free
                                            :space/occupied []}]}
                             {:row/id 1
                              :row/number 1
                              :row/type :row-type/spaces
                              :row/spaces [{:space/id 1
                                            :space/number 1
                                            :space/status :free
                                            :space/occupied []}
                                           {:space/id 2
                                            :space/number 2
                                            :space/status :occupied
                                            :space/occupied [{:occupied/id 1
                                                              :occupied/player :us
                                                              :occupied/steps [1]}]}
                                           {:space/id 3
                                            :space/number 3
                                            :space/status :free
                                            :space/occupied []}]}]})

(comment
  (swap! (::app/state-atom app) assoc-in [:occupied/id 1 :occupied/steps] [1 2])
  (app/current-state app)

  ; append a block step to a plan
  (merge/merge-component! app Step {:step/id 4
                                    :step/number 4
                                    :step/blocks [{:block/id 2
                                                   :block/row-number 2
                                                   :block/space-number 3}]
                                    :step/positions []}
                          :append [:plan/id 1 :plan/steps])

  ; update newly occupied space
  ; set status to blocked
  ; this adds a :root/space to the database - not sure that's right
  (merge/merge-component! app Space {:space/id 6
                                     :space/status :blocked}
                          :replace [:root/space])

  ; append :blocked to newly blocked space
  (merge/merge-component! app Occupied {:occupied/id 4
                                        :occupied/player :us
                                        :occupied/steps [4]}
                          :append [:space/id 6 :space/occupied])

  ; append steps step number to occupied of player occupied space
  ; --- not the way to be done in live - this adds a :root/occupied to the database - not sure that's right
  (merge/merge-component! app Occupied {:occupied/id 3
                                     :occupied/steps [3 4]}
                          :replace [:root/occupied])

  ; append a movement step to a plan
  (merge/merge-component! app Step {:step/id 3
                                    :step/number 3
                                    :step/blocks []
                                    :step/positions [{:position/id 2
                                                      :position/row-number 2
                                                      :position/space-number 2}]}
                          :append [:plan/id 1 :plan/steps])
  ; update newly occupied space
  ; set status -  - this adds a :root/space to the database - not sure that's right
  (app/current-state app)
  (merge/merge-component! app Space {:space/id 5
                                     :space/status :occupied}
                          :replace [:root/space])
  ; append :occupied to newly occupied space
  (merge/merge-component! app Occupied {:occupied/id 3
                                        :occupied/player :us
                                        :occupied/steps [3]}
                          :append [:space/id 5 :space/occupied])


  )