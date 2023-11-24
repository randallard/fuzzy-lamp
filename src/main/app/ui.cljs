(ns app.ui
  (:require
    [app.application :refer [app]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]))


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
       (dom/div {:style {:width "100%"}}
                (dom/div {:style {:float "left" :padding "10px"}}
                         (dom/h2 {} "Board [" id "] Size " size)
                         (dom/div {} (map ui-row rows)
                                  (dom/button {:onClick #(comp/transact! this `[(make-active {:board/id ~id})])
                                               :style {:margin "0px 15px"}} "make active")
                                  (dom/button {:onClick #(print "play board id " id)
                                               :style {:margin "0px 15px"}} "play against the active board")))))
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
                     (let [last-row-id    (last (sort (filter number? (keys (map #(identity %) (get-in @state [:row/id]))))))
                           space-init-id  (last (sort (filter number? (keys (map #(identity %) (get-in @state [:space/id]))))))]
                       (merge/merge-component! app Board {:board/id id :board/size size :board/step-number 0
                                                          :board/rows (new-board-rows last-row-id space-init-id size)}
                                               :replace [:root/board]))
                     (swap! state assoc-in [:state-data/id :board :state-data/state] :planning)
                     (swap! state assoc-in [:state-data/id :board :state-data/active-id] id)
                     (let [rows (get-in @state [:board/id id :board/rows])
                           start-row (filter (fn [row] (= 1 (get-in @state (conj row :row/number)))) rows)
                           start-spaces (get-in @state (conj (first start-row) :row/spaces))
                           start-space (filter (fn [space] (= 2 (get-in @state (conj space :space/number)))) start-spaces)
                           start-space-id (get-in @state (conj (first start-space) :space/id))]
                       (swap! state assoc-in [:state-data/id :board :state-data/player-space-id] start-space-id))))
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
  (dom/div {} (dom/p {} "state data " (str id " size " size " state " state " active board id " active-id " current player space " player-space-id)) ))
(def ui-state-data (comp/factory StateData {:keyfn :state-data/id}))
(defsc SavedBoards [this {:saved-boards/keys [board-size boards] :as props}]
  {:query [:saved-boards/board-size {:saved-boards/boards (comp/get-query Board)}]
   :ident :saved-boards/board-size
   :initial-state {:saved-boards/board-size :param/board-size
                   :saved-boards/boards []}}
  (dom/div {} (dom/h2 {} "board size " board-size) (map ui-board boards)))
(def ui-saved-boards (comp/factory SavedBoards {:keyfn :saved-boards/board-size}))
(defsc Root [this {:root/keys [board state-data saved-boards]}]
       {:query [{:root/board (comp/get-query Board)} {:root/state-data (comp/get-query StateData)}
                {:root/saved-boards (comp/get-query SavedBoards)}]
        :initial-state {:root/board {:id 1 :size 0 :step-number 1}
                        :root/state-data {:id :board :size 0 :active-id 1 :player-space-id 2 :state :choose-size}
                        :root/saved-boards [{:board-size 2} {:board-size 3}]}}
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
                          (get-new-board-button {:id (inc (:board/id board)) :size 2})
                          (get-new-board-button {:id (inc (:board/id board)) :size 3}))
                 :else (dom/div
                         (dom/div {}
                           (get-new-board-button {:id (inc (:board/id board)) :size 2 :label "new board size 2"})
                           (get-new-board-button {:id (inc (:board/id board)) :size 3 :label "new board size 3"}))
                         (dom/div {:style {:margin "15px"}}
                                  (dom/button {:onClick #(comp/transact! this [(make-occupied {:space/id (:state-data/player-space-id state-data)})])
                                               :style {:margin "0px 15px"}} "start")
                                  (get-new-board-button {:id (:board/id board) :size (:board/size board) :label "clear spaces"})
                                  (dom/button {:onClick #(comp/transact! this [(save-board {:board/id (:state-data/active-id state-data)})])
                                               :style {:margin "0px 15px"}} "save board"))
                         (ui-board board)
                         (dom/div {:style {:clear "left"}} (dom/h1 {} "Saved Boards")
                                  (map ui-saved-boards saved-boards)))))))
