(ns app.ui
  (:require
    [app.application :refer [app]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]))
(defn get-last-id [{:keys [state component-id]}]
  (last (sort (filter number? (keys (map #(identity %) (get-in @state [component-id])))))))
(defmutation make-blocked [{:space/keys [id]}]
             (action [{:keys [state]}]
                     (swap! state assoc-in [:space/id id :space/occupant] :blocker)
                     (let [player-space-id (get-in @state [:state-data/id :board :state-data/player-space-id])
                           old-player-space-steps (get-in @state [:space/id player-space-id :space/occupied-steps])
                           active-id (get-in @state [:state-data/id :board :state-data/active-id])
                           old-steps (get-in @state [:space/id id :space/occupied-steps])
                           new-step-number (inc (get-in @state [:board/id active-id :board/step-number]))
                           new-steps (conj old-steps new-step-number)
                           new-player-space-steps (conj old-player-space-steps new-step-number)]
                       (swap! state assoc-in [:space/id player-space-id :space/occupied-steps] new-player-space-steps)
                       (swap! state assoc-in [:space/id id :space/blocked-step] new-step-number)
                       (swap! state assoc-in [:space/id id :space/show-move-block-button] false)
                       (swap! state assoc-in [:board/id active-id :board/step-number] new-step-number))))
(defmutation make-occupied [{:keys [space/id]}]
  (action [{:keys [state]}]
          (swap! state assoc-in [:space/id id :space/occupant] :player)
          (let [active-id (get-in @state [:state-data/id :board :state-data/active-id])
                board-size (get-in @state [:board/id active-id :board/size])]
            (let [old-steps (get-in @state [:space/id id :space/occupied-steps])
                  new-step-number (inc (get-in @state [:board/id active-id :board/step-number]))
                  new-steps (conj old-steps new-step-number)]
              (swap! state assoc-in [:state-data/id :board :state-data/player-space-id] id)
              (swap! state assoc-in [:space/id id :space/occupied-steps] new-steps)
              (swap! state assoc-in [:board/id active-id :board/step-number] new-step-number))
            ; reset show-move-block-button for each space
            (let [clicked-space-row (get-in @state [:space/id id :space/row])
                  clicked-space-number (get-in @state [:space/id id :space/number])
                  rows (get-in @state [:board/id active-id :board/rows])
                  new-state (map (fn [row]
                                   (let [row-spaces (get-in @state (conj row :row/spaces))
                                         row-number (get-in @state (conj row :row/number))]
                                     (vec (map (fn [row-space]
                                                 (let [space-number (get-in @state (conj row-space :space/number))
                                                       occupant (get-in @state (conj row-space :space/occupant))
                                                       is-blocked (= occupant :blocker)
                                                       is-in-same-row (= clicked-space-row row-number)
                                                       is-one-space-away (or (= 1 (- clicked-space-number space-number))
                                                                             (= 1 (- space-number clicked-space-number)))
                                                       is-one-row-away (or (= 1 (- clicked-space-row row-number))
                                                                           (= 1 (- row-number clicked-space-row)))
                                                       is-same-space-number (= clicked-space-number space-number)
                                                       is-adjascent (cond (and is-in-same-row is-one-space-away) true
                                                                          (and is-one-row-away is-same-space-number) true
                                                                          :else false)
                                                       goal-reached (< board-size clicked-space-row)]
                                                   (swap! state assoc-in (conj row-space :space/show-move-block-button)
                                                          (and is-adjascent (not is-blocked) (not goal-reached)))))
                                               row-spaces)))) rows)] (str "new state " new-state)))))
(defn space-css [type occupant]
  {:className (str "space "
                   (if (= occupant :player) "occupied ")
                   (if (= occupant :blocker) "blocked ")
                   (if (= type :row-type/goal) "goal "))})
(defsc Space [this {:space/keys [id number occupant occupied-steps blocked-step show-move-block-button row]}]
       {:query [:space/id :space/number :space/row :space/occupant :space/occupied-steps :space/blocked-step :space/show-move-block-button]
        :ident :space/id
        :initial-state (fn [{:keys [id number occupant show-move-block-button row]}]
                         {:space/id id
                          :space/show-move-block-button show-move-block-button
                          :space/number number
                          :space/row row
                          :space/occupant occupant
                          :space/occupied-steps (cond (= occupant :player) [1]
                                                      :else [])})}
       (dom/span (space-css nil occupant)
                 (dom/button {:disabled (not show-move-block-button)
                              :onClick #(comp/transact! this [(make-occupied {:space/id id})]) :style {:margin "0px 15px"}}
                             " move ")
                 #_(str "space number " number " id " id " " occupant " occupied " occupied-steps " blocked " blocked-step)
                 number
                 (dom/button {:disabled (not show-move-block-button)
                              :onClick #(comp/transact! this [(make-blocked {:space/id id})]) :style {:margin "0px 15px"}}
                             " block ")))
(def ui-space (comp/factory Space {:keyfn :space/id}))
(defsc Row [this {:row/keys [id number type spaces] :as props}]
  {:query         [:row/id :row/number :row/type {:row/spaces (comp/get-query Space)}]
   :ident         :row/id
   :initial-state (fn [{:keys [id number type]}]
                    {:row/id     id
                     :row/number number
                     :row/type   type
                     :row/spaces (let [row-number number] (cond (> id 3) [(comp/get-initial-state Space {
                                                                                :id             (+ (* (- row-number 1) 3) 1)
                                                                                :number         1
                                                                                :row row-number
                                                                                :occupant       nil
                                                                                :show-move-block-button (cond (= row-number 1) true
                                                                                                              :else false)
                                                                                })
                                                 (comp/get-initial-state Space {:id       (+ (* (- row-number 1) 3) 2)
                                                                                :number   2
                                                                                :row row-number
                                                                                :show-move-block-button (cond (= row-number 2) true
                                                                                                              :else false)
                                                                                :occupant (cond (= row-number 1) :player
                                                                                                :else nil)})
                                                 (comp/get-initial-state Space {:id       (+ (* (- row-number 1) 3) 3)
                                                                                :number   3
                                                                                :row row-number
                                                                                :show-move-block-button (cond (= row-number 1) true
                                                                                                              :else false)
                                                                                :occupant nil})]
                                       :else [(comp/get-initial-state Space {:id       (+ (* (- row-number 1) 3) 1)
                                                                             :number   1
                                                                             :row row-number
                                                                             :show-move-block-button (cond (= row-number 1) true
                                                                                                           :else false)
                                                                             :occupant nil})
                                              (comp/get-initial-state Space {:id       (+ (* (- row-number 1) 3) 2)
                                                                             :number   2
                                                                             :row row-number
                                                                             :show-move-block-button (cond (= row-number 2) true
                                                                                                           :else false)
                                                                             :occupant (cond (= row-number 1) :player
                                                                                             :else nil)})]))})}
       (dom/div {} #_(str "row id " id " number " number) (dom/div {:style {:padding "5px"}} number (map ui-space spaces))))
(def ui-row (comp/factory Row {:keyfn :row/id}))
(defsc Board [this {:board/keys [id size rows step-number] :as props}]
       {:query [:board/id :board/size :board/step-number {:board/rows (comp/get-query Row)}]
        :ident :board/id
        :initial-state {:board/id :param/id
                        :board/size :param/size
                        :board/step-number :param/step-number
                        :board/rows [{:id 7
                                      :number 4
                                      :type :row-type/goal}
                                     {:id 6
                                      :number 3
                                      :type :row-type/space}
                                     {:id 5
                                      :number 2
                                      :type :row-type/space}
                                     {:id 4
                                      :number 1
                                      :type :row-type/space}
                                     {:id 3
                                      :number 3
                                      :type :row-type/goal}
                                     {:id 2
                                      :number 2
                                      :type :row-type/spaces}
                                     {:id 1
                                      :number 1
                                      :type :row-type/spaces}]}}
       (dom/div {}
                (dom/div {:style {:float "left" :padding "10px"}}
                         (dom/h2 {} "Board [" id "] Size " size)
                         (dom/div {} (map ui-row rows)
                                  (dom/button {:onClick #(comp/transact! this `[(make-active {:board/id ~id})])
                                               :style {:margin "0px 15px"}} "make this the player board")
                                  (dom/button {:onClick #(comp/transact! this `[(init-round {:board/id ~id})])
                                               :style {:margin "0px 15px"}} "play against the player board")))))
(def ui-board (comp/factory Board {:keyfn :board/id}))
(defmutation make-active [{:board/keys [id]}]
  (action [{:keys [state]}]
          (let [board (get-in @state [:board/id id])]
            (merge/merge-component! app Board board
                                    :replace [:root/board]))))
(defn new-board-rows [last-row-id space-init-id size]
  (cond (= size 2) [(let [id (+ last-row-id size 1)
         last-space-id (+ space-init-id (* size 0))
         row-number (+ size 1)
         row-type :row-type/goal] {:row/id id
                                   :row/number row-number
                                   :row/type row-type
                                   :row/spaces [(let [id (+ last-space-id 1)
                                                      show-move-block-button false
                                                      number 1
                                                      row row-number
                                                      occupant nil
                                                      occupied-steps []] {:space/id id
                                                                          :space/show-move-block-button show-move-block-button
                                                                          :space/number number
                                                                          :space/row row
                                                                          :space/blocked-step nil
                                                                          :space/occupant occupant
                                                                          :space/occupied-steps occupied-steps})
                                                (let [id (+ last-space-id 2)
                                                      show-move-block-button false
                                                      number 2
                                                      row row-number
                                                      occupant nil
                                                      occupied-steps []] {:space/id id
                                                                          :space/show-move-block-button show-move-block-button
                                                                          :space/number number
                                                                          :space/row row
                                                                          :space/blocked-step nil
                                                                          :space/occupant occupant
                                                                          :space/occupied-steps occupied-steps})]})
   (let [id (+ last-row-id size 0)
         last-space-id (+ space-init-id (* size 1))
         row-number (+ size 0)
         row-type :row-type/spaces] {:row/id id
                                     :row/number row-number
                                     :row/type row-type
                                     :row/spaces [(let [id (+ last-space-id 1)
                                                        show-move-block-button false
                                                        number 1
                                                        row row-number
                                                        occupant nil
                                                        occupied-steps []] {:space/id id
                                                                            :space/show-move-block-button show-move-block-button
                                                                            :space/number number
                                                                            :space/row row
                                                                            :space/blocked-step nil
                                                                            :space/occupant occupant
                                                                            :space/occupied-steps occupied-steps})
                                                  (let [id (+ last-space-id 2)
                                                        show-move-block-button false
                                                        number 2
                                                        row row-number
                                                        occupant nil
                                                        occupied-steps []] {:space/id id
                                                                            :space/show-move-block-button show-move-block-button
                                                                            :space/number number
                                                                            :space/row row
                                                                            :space/blocked-step nil
                                                                            :space/occupant occupant
                                                                            :space/occupied-steps occupied-steps})]})
   (let [id (+ last-row-id size -1)
         last-space-id (+ space-init-id (* size 2))
         row-number (+ size -1)
         row-type :row-type/spaces] {:row/id id
                                     :row/number row-number
                                     :row/type row-type
                                     :row/spaces [(let [id (+ last-space-id 1)
                                                        show-move-block-button false
                                                        number 1
                                                        row row-number
                                                        occupant nil
                                                        occupied-steps []] {:space/id id
                                                                            :space/show-move-block-button show-move-block-button
                                                                            :space/number number
                                                                            :space/row row
                                                                            :space/blocked-step nil
                                                                            :space/occupant occupant
                                                                            :space/occupied-steps occupied-steps})
                                                  (let [id (+ last-space-id 2)
                                                        show-move-block-button false
                                                        number 2
                                                        row row-number
                                                        occupant nil
                                                        occupied-steps []] {:space/id id
                                                                            :space/show-move-block-button show-move-block-button
                                                                            :space/number number
                                                                            :space/row row
                                                                            :space/blocked-step nil
                                                                            :space/occupant occupant
                                                                            :space/occupied-steps occupied-steps})]})]
  (= size 3) [(let [id (+ last-row-id size 1)
                                           last-space-id (+ space-init-id (* size 0))
                                           row-number (+ size 1)
                                           row-type :row-type/goal] {:row/id id
                                                                     :row/number row-number
                                                                     :row/type row-type
                                                                     :row/spaces [(let [id (+ last-space-id 1)
                                                                                        show-move-block-button false
                                                                                        number 1
                                                                                        row row-number
                                                                                        occupant nil
                                                                                        occupied-steps []] {:space/id id
                                                                                                            :space/show-move-block-button show-move-block-button
                                                                                                            :space/number number
                                                                                                            :space/row row
                                                                                                            :space/blocked-step nil
                                                                                                            :space/occupant occupant
                                                                                                            :space/occupied-steps occupied-steps})
                                                                                  (let [id (+ last-space-id 2)
                                                                                        show-move-block-button false
                                                                                        number 2
                                                                                        row row-number
                                                                                        occupant nil
                                                                                        occupied-steps []] {:space/id id
                                                                                                            :space/show-move-block-button show-move-block-button
                                                                                                            :space/number number
                                                                                                            :space/row row
                                                                                                             :space/blocked-step nil
                                                                                                            :space/occupant occupant
                                                                                                            :space/occupied-steps occupied-steps})
                                                                                  (let [id (+ last-space-id 3)
                                                                                        show-move-block-button false
                                                                                        number 3
                                                                                        row row-number
                                                                                        occupant nil
                                                                                        occupied-steps []] {:space/id id
                                                                                                            :space/show-move-block-button show-move-block-button
                                                                                                            :space/number number
                                                                                                            :space/row row
                                                                                                            :space/blocked-step nil
                                                                                                            :space/occupant occupant
                                                                                                            :space/occupied-steps occupied-steps})]})
                                     (let [id (+ last-row-id size 0)
                                           last-space-id (+ space-init-id (* size 1))
                                           row-number (+ size 0)
                                             row-type :row-type/spaces] {:row/id id
                                                                       :row/number row-number
                                                                       :row/type row-type
                                                                       :row/spaces [(let [id (+ last-space-id 1)
                                                                                          show-move-block-button false
                                                                                          number 1
                                                                                          row row-number
                                                                                          occupant nil
                                                                                          occupied-steps []] {:space/id id
                                                                                                              :space/show-move-block-button show-move-block-button
                                                                                                              :space/number number
                                                                                                              :space/row row
                                                                                                              :space/blocked-step nil
                                                                                                              :space/occupant occupant
                                                                                                              :space/occupied-steps occupied-steps})
                                                                                    (let [id (+ last-space-id 2)
                                                                                          show-move-block-button false
                                                                                          number 2
                                                                                          row row-number
                                                                                          occupant nil
                                                                                          occupied-steps []] {:space/id id
                                                                                                              :space/show-move-block-button show-move-block-button
                                                                                                              :space/number number
                                                                                                              :space/row row
                                                                                                              :space/blocked-step nil
                                                                                                              :space/occupant occupant
                                                                                                              :space/occupied-steps occupied-steps})
                                                                                    (let [id (+ last-space-id 3)
                                                                                          show-move-block-button false
                                                                                          number 3
                                                                                          row row-number
                                                                                          occupant nil
                                                                                          occupied-steps []] {:space/id id
                                                                                                              :space/show-move-block-button show-move-block-button
                                                                                                              :space/number number
                                                                                                              :space/row row
                                                                                                              :space/blocked-step nil
                                                                                                              :space/occupant occupant
                                                                                                              :space/occupied-steps occupied-steps})]})
                                       (let [id (+ last-row-id size -1)
                                             last-space-id (+ space-init-id (* size 2))
                                             row-number (+ size -1)
                                             row-type :row-type/spaces] {:row/id id
                                                                         :row/number row-number
                                                                         :row/type row-type
                                                                         :row/spaces [(let [id (+ last-space-id 1)
                                                                                            show-move-block-button false
                                                                                            number 1
                                                                                            row row-number
                                                                                            occupant nil
                                                                                            occupied-steps []] {:space/id id
                                                                                                                :space/show-move-block-button show-move-block-button
                                                                                                                :space/number number
                                                                                                                :space/row row
                                                                                                               :space/blocked-step nil
                                                                                                                :space/occupant occupant
                                                                                                                :space/occupied-steps occupied-steps})
                                                                                      (let [id (+ last-space-id 2)
                                                                                            show-move-block-button false
                                                                                            number 2
                                                                                            row row-number
                                                                                            occupant nil
                                                                                            occupied-steps []] {:space/id id
                                                                                                                :space/show-move-block-button show-move-block-button
                                                                                                                :space/number number
                                                                                                                :space/row row
                                                                                                                :space/blocked-step nil
                                                                                                                :space/occupant occupant
                                                                                                                :space/occupied-steps occupied-steps})
                                                                                      (let [id (+ last-space-id 3)
                                                                                            show-move-block-button false
                                                                                            number 3
                                                                                            row row-number
                                                                                            occupant nil
                                                                                            occupied-steps []] {:space/id id
                                                                                                                :space/show-move-block-button show-move-block-button
                                                                                                                :space/number number
                                                                                                                :space/row row
                                                                                                                :space/blocked-step nil
                                                                                                                :space/occupant occupant
                                                                                                                :space/occupied-steps occupied-steps})]})
                                       (if (= 3 size) (let [id (+ last-row-id size -2)
                                             last-space-id (+ space-init-id (* size 3))
                                             row-number (+ size -2)
                                             row-type :row-type/spaces] {:row/id id
                                                                         :row/number row-number
                                                                         :row/type row-type
                                                                         :row/spaces [(let [id (+ last-space-id 1)
                                                                                            show-move-block-button false
                                                                                            number 1
                                                                                            row row-number
                                                                                            occupant nil
                                                                                            occupied-steps []] {:space/id id
                                                                                                                :space/show-move-block-button show-move-block-button
                                                                                                                :space/number number
                                                                                                                :space/row row
                                                                                                                :space/blocked-step nil
                                                                                                                :space/occupant occupant
                                                                                                                :space/occupied-steps occupied-steps})
                                                                                      (let [id (+ last-space-id 2)
                                                                                            show-move-block-button false
                                                                                            number 2
                                                                                            row row-number
                                                                                            occupant nil
                                                                                            occupied-steps []] {:space/id id
                                                                                                                :space/show-move-block-button show-move-block-button
                                                                                                                :space/number number
                                                                                                                :space/row row
                                                                                                                :space/blocked-step nil
                                                                                                                :space/occupant occupant
                                                                                                                :space/occupied-steps occupied-steps})
                                                                                      (let [id (+ last-space-id 3)
                                                                                            show-move-block-button false
                                                                                            number 3
                                                                                            row row-number
                                                                                            occupant nil
                                                                                            occupied-steps []] {:space/id id
                                                                                                                :space/show-move-block-button show-move-block-button
                                                                                                                :space/number number
                                                                                                                :space/row row
                                                                                                                :space/blocked-step nil
                                                                                                                :space/occupant occupant
                                                                                                                :space/occupied-steps occupied-steps})]}))]))
(defmutation get-board [{:board/keys [id size]}]
             (action [{:keys [state]}]
                     (swap! state assoc-in [:state-data/id :board :state-data/size] size)
                     (let [new-board-id    (inc (get-last-id {:state state :component-id :board/id}))
                           last-row-id (get-last-id {:state state :component-id :row/id})
                           space-init-id  (get-last-id {:state state :component-id :space/id})]
                       (merge/merge-component! app Board {:board/id new-board-id :board/size size :board/step-number 0
                                                          :board/rows (new-board-rows last-row-id space-init-id size)}
                                               :replace [:root/board])
                       (swap! state assoc-in [:state-data/id :board :state-data/state] :planning)
                       (swap! state assoc-in [:state-data/id :board :state-data/active-id] new-board-id)
                       (let [rows (get-in @state [:board/id new-board-id :board/rows])
                             start-row (filter (fn [row] (= 1 (get-in @state (conj row :row/number)))) rows)
                             start-spaces (get-in @state (conj (first start-row) :row/spaces))
                             start-space (filter (fn [space] (= 2 (get-in @state (conj space :space/number)))) start-spaces)
                             start-space-id (get-in @state (conj (first start-space) :space/id))]
                         (swap! state assoc-in [:state-data/id :board :state-data/player-space-id] start-space-id))))
                     )
(defmutation save-board [{:board/keys [id]}]
  (action [{:keys [state]}]
          (let [board (get-in @state [:board/id id])
                saved-board-size (get-in @state [:board/id id :board/size])]
            (merge/merge-component! app Board board
                                    :append [:saved-boards/board-size saved-board-size :saved-boards/boards]))))
(defsc StateData [this {:state-data/keys [id active-id player-space-id size state] :as props}]
  {:query [:state-data/id :state-data/active-id :state-data/player-space-id :state-data/size :state-data/state]
   :ident :state-data/id
   :initial-state {:state-data/id :param/id
                   :state-data/active-id :param/active-id
                   :state-data/player-space-id :param/player-space-id
                   :state-data/size :param/size
                   :state-data/state :param/state}}
  #_(dom/div {} (dom/p {} "state data " (str id " size " size " state " state " player board id " active-id " current player space " player-space-id)) ))
(def ui-state-data (comp/factory StateData {:keyfn :state-data/id}))
(defsc SavedBoards [this {:saved-boards/keys [board-size boards] :as props}]
  {:query [:saved-boards/board-size {:saved-boards/boards (comp/get-query Board)}]
   :ident :saved-boards/board-size
   :initial-state {:saved-boards/board-size :param/board-size
                   :saved-boards/boards []}}
  (dom/div {:style {:clear "left"}} (dom/h2 {} "board size " board-size) (map ui-board boards)))
(def ui-saved-boards (comp/factory SavedBoards {:keyfn :saved-boards/board-size}))
(defn get-score [{:keys [player-board opponent-board]}]
  (print "player")
  (print (str player-board))
  (print "opponent")
  (print (str opponent-board))
  (str "Player Points: " " Opponent Points: "))
(defsc ResultsSpace [this {:results-space/keys [id] :as props}]
  {:query [:results-space/id]
   :ident :results-space/id}
  (dom/div {:style {:float "left"}} (dom/p "space " id)))
(def ui-results-space (comp/factory ResultsSpace {:keyfn :results-space/id}))
(defsc ResultsRow [this {:results-row/keys [id results-spaces] :as props}]
  {:query [:results-row/id {:results-row/results-spaces (comp/get-query ResultsSpace)}]
   :ident :results-row/id}
  (dom/div {:style {:float "left" :clear "left"}} (map ui-results-space results-spaces)))
(def ui-results-row (comp/factory ResultsRow {:keyfn :results-row/id}))
(defsc ResultsBoard [this {:results-board/keys [id results-rows] :as props}]
  {:query [:results-board/id {:results-board/results-rows (comp/get-query ResultsRow)}]
   :ident :results-board/id}
  (dom/div {:style {:float "left"}} (dom/h4 {} "results board")
           (map ui-results-row results-rows)))
(def ui-results-board (comp/factory ResultsBoard {:keyfn :results-board/id}))
(defsc Round [this {:round/keys [id number player-board opponent-board results-board] :as props}]
  {:query [:round/id
           :round/number
           {:round/player-board (comp/get-query Board)}
           {:round/opponent-board (comp/get-query Board)}
           {:round/results-board (comp/get-query ResultsBoard)}]
   :ident :round/id}
  (dom/div {:style {:float "left" :clear "left" :border "thin solid black"}}
           (dom/h3 {} "Round[" id "] Number " number)
           (dom/div {:style {:float "left"}} "results board" (ui-results-board results-board))
           (dom/div {:style {:float "left" :clear "left"}} "player board" (ui-board player-board))
           (dom/div {:style {:float "left"}} "opponent board" (ui-board opponent-board))))
(def ui-round (comp/factory Round {:keyfn :round/id}))
(defsc Match [this {:match/keys [id rounds] :as props}]
  {:query [:match/id {:match/rounds (comp/get-query Round)}]
   :ident :match/id
   :initial-state {:match/id :param/id
                   :match/rounds []}}
  (dom/div {:style {:clear "left"}} (dom/h3 {} "Match " id )
           (map ui-round rounds)))
(def ui-match (comp/factory Match {:keyfn :match/id}))
(defmutation init-round [{:keys [board/id]}]
  (action [{:keys [state]}]
          (let [match-id (get-last-id {:state state :component-id :match/id})
                last-round-id (get-last-id {:state state :component-id :round/id})
                last-round-number (get-in @state [:round/id last-round-id :round/number])
                new-round-id (inc last-round-id)
                new-round-number (inc last-round-number)
                player-board-id (get-in @state [:state-data/id :board :state-data/active-id])
                player-board (get-in @state [:board/id player-board-id])
                player-rows (get-in @state [:board/id player-board-id :board/rows])
                opponent-board (get-in @state [:board/id id])
                opponent-rows (reverse (get-in @state [:board/id id :board/rows]))
                results-board-id (inc (get-last-id {:state state :component-id :results-board/id}))
                last-row-id (get-last-id {:state state :component-id :results-row/id})
                last-space-id (get-last-id {:state state :component-id :results-space/id})
                round {:round/id new-round-id
                       :round/number new-round-number
                       :round/player-board player-board
                       :round/opponent-board opponent-board
                       :round/results-board { :results-board/id results-board-id
                                              :results-board/results-rows [{:results-row/id (+ 1 last-row-id)
                                                                            :results-row/results-spaces [{:results-space/id (+ 1 last-space-id)
                                                                                                          :results-space/number 1}]}
                                                                           {:results-row/id (+ 2 last-row-id)
                                                                            :results-row/results-spaces [{:results-space/id (+ 2 last-space-id)
                                                                                                          :results-space/number 2}]}
                                                                           {:results-row/id (+ 3 last-row-id)
                                                                            :results-row/results-spaces [{:results-space/id (+ 3 last-space-id)
                                                                                                          :results-space/number 3}]}]}}]
            (print (str "player" player-rows))
            (print (str "opponent" opponent-rows))
            (merge/merge-component! app Round round
                                      :append [:match/id match-id :match/rounds]))))
(defsc Root [this {:root/keys [board state-data saved-boards match]}]
       {:query [{:root/board (comp/get-query Board)}
                {:root/state-data (comp/get-query StateData)}
                {:root/saved-boards (comp/get-query SavedBoards)}
                {:root/match (comp/get-query Match)}]
        :initial-state {:root/board {:id 1 :size 0 :step-number 1}
                        :root/state-data {:id :board :size 0 :active-id 1 :player-space-id 2 :state :choose-size}
                        :root/saved-boards [{:board-size 2} {:board-size 3}]
                        :root/match {:id 1}}}
  (dom/div {}
           (ui-state-data state-data)
           (let [get-new-board-button (fn [{:keys [id size label]}]
                                        (dom/button {:onClick #(comp/transact! this
                                                                               [(get-board {:board/id id
                                                                                            :board/size size})])
                                                     :style {:margin "0px 15px"}} (str " " (cond (nil? label) size
                                                                                                 :else        label)
                                                                                       " ")))]
             (cond (= (:state-data/state state-data) :choose-size)
                 (dom/div {} "select board size: "
                          (get-new-board-button {:id (:board/id board) :size 2})
                          (get-new-board-button {:id (:board/id board) :size 3}))
                 :else (dom/div
                         (dom/div {}
                           (get-new-board-button {:id (:board/id board) :size 2 :label "new board size 2"})
                           (get-new-board-button {:id (:board/id board) :size 3 :label "new board size 3"}))
                         (dom/div {:style {:margin "15px"}}
                                  (dom/button {:onClick #(comp/transact! this [(make-occupied {:space/id (:state-data/player-space-id state-data)})])
                                               :style {:margin "0px 15px"}} "start")
                                  (get-new-board-button {:id (:board/id board) :size (:board/size board) :label "clear spaces"})
                                  (dom/button {:onClick #(comp/transact! this [(save-board {:board/id (:state-data/active-id state-data)})])
                                               :style {:margin "0px 15px"}} "save board"))
                         (ui-board board)
                         (dom/div {:style {:clear "left"}} (dom/h1 {} "Match")
                                  (ui-match match))
                         (dom/div {} (dom/h1 {:style {:clear "left"}} "Saved Boards")
                                  (map ui-saved-boards saved-boards)))))))
