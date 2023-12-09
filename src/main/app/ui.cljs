(ns app.ui
  (:require
    [clojure.set :refer [intersection]]
    [app.application :refer [app]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]))
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
                                         row-number (get-in @state (conj row :row/number))
                                         row-type (get-in @state (conj row :row/type))]
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
                                                       is-opponent-goal (= row-type :row-type/opponent-goal)
                                                       goal-reached (< board-size clicked-space-row)]
                                                   (swap! state assoc-in (conj row-space :space/show-move-block-button)
                                                          (and is-adjascent (not is-blocked) (not is-opponent-goal) (not goal-reached)))))
                                               row-spaces)))) rows)] (str "new state " new-state)))))
(defn space-css [type occupant]
  {:className (str "space "
                   (if (= occupant :player) "occupied ")
                   (if (= occupant :blocker) "blocked ")
                   (if (= type :row-type/goal) "goal "))})
(defsc Space [this {:space/keys [id number occupant occupied-steps blocked-step show-move-block-button row]}]
       {:query [:space/id :space/number :space/row :space/occupant :space/occupied-steps :space/blocked-step :space/show-move-block-button]
        :ident :space/id}
       (dom/span (space-css nil occupant)
                 (dom/button {:disabled (not show-move-block-button)
                              :onClick #(comp/transact! this [(make-occupied {:space/id id})]) :style {:margin "0px 15px"}}
                             " move ")
                 #_(str "space number " number " id " id " " occupant " occupied " occupied-steps " blocked " blocked-step)
                 (dom/button {:disabled (not show-move-block-button)
                              :onClick #(comp/transact! this [(make-blocked {:space/id id})]) :style {:margin "0px 15px"}}
                             " block ")))
(def ui-space (comp/factory Space {:keyfn :space/id}))
(defsc Row [this {:row/keys [id number type spaces] :as props}]
  {:query         [:row/id :row/number :row/type {:row/spaces (comp/get-query Space)}]
   :ident         :row/id}
  (dom/div {} #_(str "row id " id " number " number " type " type) (dom/div {:style {:padding "5px"}} (map ui-space spaces))))
(def ui-row (comp/factory Row {:keyfn :row/id}))
(defsc Board [this {:board/keys [id size rows step-number] :as props}]
       {:query [:board/id :board/size :board/step-number {:board/rows (comp/get-query Row)}]
        :ident :board/id
        :initial-state {:board/id :param/id
                        :board/size :param/size
                        :board/step-number :param/step-number}}
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
(defmutation get-board [{:board/keys [id size]}]
             (action [{:keys [state]}]
                     (swap! state assoc-in [:state-data/id :board :state-data/size] size)
                     (let [new-board-id    (inc (get-last-id {:state state :component-id :board/id}))
                           last-row-id (get-last-id {:state state :component-id :row/id})
                           space-init-id  (get-last-id {:state state :component-id :space/id})
                           rows (map (fn [row-number] (let [this-row-first-space-id (+ (inc space-init-id) (* row-number size))
                                                            row {:row/id     (inc (+ last-row-id row-number))
                                                                 :row/number row-number
                                                                 :row/type   (cond (= row-number 0) :row-type/opponent-goal
                                                                                   (= row-number (+ 1 size)) :row-type/goal
                                                                                   :else :row-type/spaces)
                                                                 :row/spaces (vec (map (fn [space-number] (let [space {:space/id (inc (+ this-row-first-space-id space-number))
                                                                                                                       :space/number (inc space-number)
                                                                                                                       :space/row row-number
                                                                                                                       :space/blocked-step nil
                                                                                                                       :space/occupant nil
                                                                                                                       :space/occupied-steps []}]
                                                                                                            space)) (range size)))}]
                                                        row)) (reverse (range (+ 2 size))))
                           board {:board/id new-board-id :board/size size :board/step-number 0
                                  :board/rows (vec rows)}]
                       (merge/merge-component! app Board board
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
(defsc ResultsSpace [this {:results-space/keys [id player-set-block-at-step player-occupied-steps opponent-set-block-at-step opponent-occupied-steps] :as props}]
  {:query [:results-space/id :results-space/player-set-block-at-step :results-space/player-occupied-steps :results-space/opponent-set-block-at-step :results-space/opponent-occupied-steps]
   :ident :results-space/id}
  (dom/div {:style {:float "left" :border "thin solid black" :padding "5px"}} #_(dom/p " space " id)
           (dom/div {:style {}}
                    " player occupied at steps " (str player-occupied-steps) (dom/br) " player set block at step " player-set-block-at-step (dom/br)
                    " opponent occupied at steps " (str opponent-occupied-steps) (dom/br) " opponent set block at step " opponent-set-block-at-step)))
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
(defn get-results-row-spaces [{:keys [state space-start-id player-spaces opponent-spaces]}]
  (let [results-spaces (map (fn [player-space opponent-space]
                              (let [player-space-row (get-in @state (conj player-space :space/row))
                                    player-set-block-at-step (get-in @state (conj player-space :space/blocked-step))
                                    player-occupied-steps (get-in @state (conj player-space :space/occupied-steps))
                                    opponent-set-block-at-step (get-in @state (conj opponent-space :space/blocked-step))
                                    opponent-occupied-steps (get-in @state (conj opponent-space :space/occupied-steps))
                                    player-block-used (cond (nil? player-set-block-at-step) false
                                                            :else true)
                                    opponent-block-used (cond (nil? opponent-set-block-at-step) false
                                                            :else true)
                                    player-was-blocked-at-row (if (and opponent-block-used (some #(<= opponent-set-block-at-step %) player-occupied-steps))
                                                                player-space-row)
                                    opponent-was-blocked-at-row (if (and player-block-used (some #(<= player-set-block-at-step %) opponent-occupied-steps))
                                                            player-space-row)
                                    players-collided-at-step (apply min (intersection (into #{} player-occupied-steps) (into #{} opponent-occupied-steps)))
                                    player-was-blocked-at-step (apply min (filter #(and (not (nil? opponent-set-block-at-step)) (<= opponent-set-block-at-step %)) player-occupied-steps))
                                    opponent-was-blocked-at-step (apply min (filter #(and (not (nil? player-set-block-at-step)) (<= player-set-block-at-step %)) opponent-occupied-steps))
                                    results-space {:results-space/players-collided-at-step players-collided-at-step
                                                   :results-space/player-block-used player-block-used
                                                   :results-space/player-was-blocked-at-row player-was-blocked-at-row
                                                   :results-space/opponent-block-used opponent-block-used
                                                   :results-space/opponent-was-blocked-at-row opponent-was-blocked-at-row
                                                   :results-space/player player-space
                                                   :results-space/opponent opponent-space
                                                   :results-space/player-set-block-at-step player-set-block-at-step
                                                   :results-space/player-occupied-steps player-occupied-steps
                                                   :results-space/opponent-set-block-at-step opponent-set-block-at-step
                                                   :results-space/opponent-occupied-steps opponent-occupied-steps
                                                   :results-space/player-was-blocked-at-step player-was-blocked-at-step
                                                   :results-space/opponent-was-blocked-at-step opponent-was-blocked-at-step}]
                                results-space)) player-spaces (reverse opponent-spaces))]
    (map #(assoc %1 :results-space/id %2) results-spaces (iterate inc space-start-id))))
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
                opponent-rows (get-in @state [:board/id id :board/rows])
                last-row-id (get-last-id {:state state :component-id :results-row/id})
                results-rows (let [results-rows (map (fn [player-row opponent-row]
                                 (let [row-type (get-in @state (conj player-row :row/type))
                                       row-number (get-in @state (conj player-row :row/number))
                                       player-spaces (get-in @state (conj player-row :row/spaces))
                                       opponent-spaces (get-in @state (conj opponent-row :row/spaces))
                                       last-space-id (get-last-id {:state state :component-id :results-space/id})
                                       this-player-row (get-in @state (conj player-row :row/number))
                                       this-row-first-space-id (+ (inc last-space-id) (* (- this-player-row 1) (count player-spaces)))
                                       results-row-spaces (get-results-row-spaces {:state state
                                                                                   :space-start-id this-row-first-space-id
                                                                                   :player-spaces player-spaces
                                                                                   :opponent-spaces opponent-spaces})
                                       opponent-blocks-used (count (filter true? (map :results-space/opponent-block-used results-row-spaces)))
                                       player-blocks-used (count (filter true? (map :results-space/player-block-used results-row-spaces)))
                                       opponent-was-blocked-at-row (first (filter #(not (nil? %)) (map :results-space/opponent-was-blocked-at-row results-row-spaces)))
                                       player-was-blocked-at-row (first (filter #(not (nil? %)) (map :results-space/player-was-blocked-at-row results-row-spaces)))
                                       players-collided-at-step (apply min (filter number? (map :results-space/players-collided-at-step results-row-spaces)))
                                       player-was-blocked-at-step (apply min (filter number? (map :results-space/player-was-blocked-at-step results-row-spaces)))
                                       opponent-was-blocked-at-step (apply min (filter number? (map :results-space/opponent-was-blocked-at-step results-row-spaces)))
                                       player-steps-in-this-row (apply min (apply concat (map #(get-in @state (conj % :space/occupied-steps)) player-spaces)))
                                       opponent-steps-in-this-row (apply min (apply concat (map #(get-in @state (conj % :space/occupied-steps)) opponent-spaces)))
                                       player-step-in-row-before-stopped (cond (and
                                                                                 (or (nil? players-collided-at-step) (< player-steps-in-this-row players-collided-at-step))
                                                                                 (or (nil? player-was-blocked-at-step) (< player-steps-in-this-row player-was-blocked-at-step)))
                                                                               player-steps-in-this-row
                                                                               :else nil)
                                       opponent-step-in-row-before-stopped (cond (and
                                                                                 (or (nil? players-collided-at-step) (< opponent-steps-in-this-row players-collided-at-step))
                                                                                 (or (nil? opponent-was-blocked-at-step) (< opponent-steps-in-this-row opponent-was-blocked-at-step)))
                                                                               opponent-steps-in-this-row
                                                                               :else nil)
                                       results-row {:results-row/player-row-type row-type
                                                    :results-row/player-row-number row-number
                                                    :results-row/player-row player-row
                                                    :results-row/opponent-row opponent-row
                                                    :results-row/results-spaces (vec results-row-spaces)
                                                    :results-row/opponent-blocks-used opponent-blocks-used
                                                    :results-row/opponent-steps-in-this-row opponent-steps-in-this-row
                                                    :results-row/opponent-was-blocked-at-step opponent-was-blocked-at-step
                                                    :results-row/opponent-was-blocked-at-row opponent-was-blocked-at-row
                                                    :results-row/opponent-steps-before-stopped opponent-step-in-row-before-stopped
                                                    :results-row/player-blocks-used player-blocks-used
                                                    :results-row/player-steps-in-this-row player-steps-in-this-row
                                                    :results-row/player-was-blocked-at-step player-was-blocked-at-step
                                                    :results-row/player-was-blocked-at-row player-was-blocked-at-row
                                                    :results-row/players-collided-at-step players-collided-at-step
                                                    :results-row/player-steps-before-stopped player-step-in-row-before-stopped}]
                                   results-row)) player-rows (reverse opponent-rows))]
                               (map #(assoc %1 :results-row/id %2) results-rows (iterate inc (inc last-row-id))))
                results-board-id (inc (get-last-id {:state state :component-id :results-board/id}))
                opponent-was-blocked-at-step (apply min (filter number? (map :results-row/opponent-was-blocked-at-step results-rows)))
                opponent-used-blocks (reduce + (map :results-row/opponent-blocks-used results-rows))
                player-was-blocked-at-step (apply min (filter number? (map :results-row/player-was-blocked-at-step results-rows)))
                player-used-blocks (reduce + (map :results-row/player-blocks-used results-rows))
                players-collided-at-step (apply min (filter number? (map :results-row/players-collided-at-step results-rows)))
                player-rows-progressed-before-stopped (count (filter #(and (> % 1)
                                                                    (not (nil? %))
                                                                    (or (nil? players-collided-at-step) (< % players-collided-at-step))
                                                                    (or (nil? player-was-blocked-at-step) (< % player-was-blocked-at-step)))
                                                              (map :results-row/player-steps-before-stopped results-rows)))
                opponent-rows-progressed-before-stopped (count (filter #(and (> % 1)
                                                                           (not (nil? %))
                                                                           (or (nil? players-collided-at-step) (< % players-collided-at-step))
                                                                           (or (nil? opponent-was-blocked-at-step) (< % opponent-was-blocked-at-step)))
                                                                     (map :results-row/opponent-steps-before-stopped results-rows)))
                round {:round/id new-round-id
                       :round/number new-round-number
                       :round/player-board player-board
                       :round/opponent-board opponent-board
                       :round/results-board {:results-board/id results-board-id
                                             :results-board/results-rows (vec results-rows)
                                             :results-board/players-collided-at-step players-collided-at-step
                                             :results-board/opponent-was-blocked-at-step opponent-was-blocked-at-step
                                             :results-board/opponent-used-blocks opponent-used-blocks
                                             :results-board/opponent-rows-progressed-before-stopped opponent-rows-progressed-before-stopped
                                             :results-board/player-was-blocked-at-step player-was-blocked-at-step
                                             :results-board/player-used-blocks player-used-blocks
                                             :results-board/player-rows-progressed-before-stopped player-rows-progressed-before-stopped}}]
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
             (dom/div {}
                      (get-new-board-button {:id (:board/id board) :size 2 :label "new board size 2"})
                      (get-new-board-button {:id (:board/id board) :size 3 :label "new board size 3"})
                      (if (not (= (:state-data/state state-data) :choose-size))
                        (dom/div
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
                                   (map ui-saved-boards saved-boards))))))))
