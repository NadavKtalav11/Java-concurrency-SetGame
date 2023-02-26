package bguspl.set.ex;

import bguspl.set.Env;


import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
//import java.util.Collection;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    public Thread dealerThread;

    // if the dealer has sets to check
    public boolean checksets;

    /**
     * private final Env env;
     * <p>
     * /**
     * Game entities.
     */
    private final Table table;

    //array of the players
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    // time the clock start
    private long starttime;

    //lock for sets time
    final public Object setsLock;

    // indicate the time is negative or positive
    private long INDICATOR_TIME = 0;

    //all players threads
    Thread[] playerThreads;

    // cards that send to check if them sets
    ArrayDeque<int[]> cards;

    // slots of the sets crads
    ArrayDeque<int[]> slots ;


    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        checksets = false;
        setsLock = new Object();
        slots= new ArrayDeque<>();
        cards= new ArrayDeque<>();

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        env.logger.info("Thread dealer " + Thread.currentThread().getName() + " starting.");
        playerThreads = new Thread[players.length];
        for (int i = 0; i < players.length; i++) {
            Thread currPlayer = new Thread(players[i]);
            playerThreads[i] = currPlayer;
            currPlayer.start();
        }
        
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();

        synchronized (Thread.currentThread()) {
            try {
                Thread.currentThread().sleep(env.config.endGamePauseMillies);
            }
            catch (InterruptedException  e){
                System.out.println("97 + dealer "+ e );
            }
        }
        terminate();
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");

    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        starttime = System.currentTimeMillis();
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 1000;
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        // TODO implement
        for (int i = players.length-1; i >= 0; i--) {
            players[i].terminate();
            while (playerThreads[i].isAlive()){}
        }
        terminate =true;
    }


    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        synchronized (setsLock) {
            while (!cards.isEmpty()) {
                int[] currCards = cards.remove();
                int currPlayer = currCards[env.config.featureSize];
                boolean nullFound = false;
                for (int j = 0; j < env.config.featureSize; j++) {
                    if (table.cardToSlot[currCards[j]] == null) {
                        nullFound = true;
                    }
                }
                if (nullFound) {
                    for (int j = 0; j < env.config.featureSize; j++) {
                        if (table.cardToSlot[currCards[j]]!=null){
                            this.env.ui.removeToken(currPlayer, table.cardToSlot[currCards[j]]);
                        }
                    }
                    players[currPlayer].penaltyPoint=2;
                    synchronized (playerThreads[currPlayer]) {
                        try {
                            playerThreads[currPlayer].notifyAll();
                        } catch (IllegalMonitorStateException e) {
                            System.out.println("dealer 206" + e);
                        }
                    }
                    return;
                }
                int [] set = new int[env.config.featureSize];
                for (int k= 0 ; k<env.config.featureSize ; k++){
                    set[k] = currCards[k];
                }
                if (env.util.testSet(set)) {

                    for (int j = 0; j < env.config.featureSize; j++) {
                        int slotNow= table.cardToSlot[currCards[j]];
                        if (players[currPlayer].numOfTokens != 0) {
                            this.env.ui.removeToken(currPlayer, table.cardToSlot[currCards[j]]);
                            table.removeCard(table.cardToSlot[currCards[j]]);
                        }
                        if (deck.size()==0){
                            for (int i=0 ; i<players.length ; i++){
                                if (i!=currPlayer) {
                                    if (players[i].tokens.contains(table.cardToSlot[set[j]])) {
                                            players[i].tokens.remove(table.cardToSlot[set[j]]);
                                            players[i].numOfTokens--;
                                            
                                    }
                                }
                    
                            }
                            env.ui.removeTokens(slotNow);
                            
                        }
                    }
                    players[currPlayer].penaltyPoint = 1;
                }
                else {
                    players[currPlayer].penaltyPoint = -1;
                }
                synchronized (playerThreads[currPlayer]) {
                    try {
                        playerThreads[currPlayer].notifyAll();
                    } catch (IllegalMonitorStateException e) {
                        System.out.println("dealer 206" + e);
                    }
                }

            }
        }
    }


        /**
         * Check if any cards can be removed from the deck and placed on the table.
         */
        private void placeCardsOnTable() {
            
            int i = 0;
            ArrayList<Integer> shuflled = new ArrayList<Integer>();
            while (i < env.config.tableSize) {
                if (deck.size() == 0) {
                    return;
                }
                if (table.slotToCard[i] == null) {
                    shuflled.add(i);
                }
                i = i + 1;
            }
            Collections.shuffle(shuflled);
            Collections.shuffle(deck);
            int j = 0;
            while (j < shuflled.size() & deck.size()>0) {
                table.placeCard(deck.remove(0), shuflled.remove(0));
                if (table.countCards() == env.config.tableSize || deck.size()==0) {
                    updateTimerDisplay(true);
                }
            }

        }

    /**
     * This method if there is a set from the card that are
     * right now on the table.
     */
     
    private boolean setOnTable() {

        int numOfCards= table.countCards() ;
        if (numOfCards==0){
            return false;
        }
        if (numOfCards == env.config.tableSize) {
            if (env.util.findSets(Arrays.asList(table.slotToCard), 1).size() == 0) {
                return false;
            }
        }
        else {
            Integer [] notNullCards = new Integer[numOfCards];
            int k = 0;
            for (int i=0 ; i<table.slotToCard.length ; i++){
                if (table.slotToCard[i]!=null){
                    notNullCards[k]=table.slotToCard[i];
                    k=k+1;
                }
            }
            if (env.util.findSets(Arrays.asList(notNullCards), 1).size() == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement

            if (reshuffleTime - System.currentTimeMillis() > env.config.turnTimeoutWarningMillis) {
                try {
                    synchronized (setsLock) {
                        setsLock.wait(env.config.tableDelayMillis);
                    }
                } catch (InterruptedException  ignored) {

                    System.out.println(ignored);
                }
            }
                else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                }
            }
        }



    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (reset){
            reshuffleTime = Long.MAX_VALUE;
            starttime = System.currentTimeMillis();
            if(env.config.turnTimeoutMillis > INDICATOR_TIME){
                reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            }
        }
        if(env.config.turnTimeoutMillis>INDICATOR_TIME){
            long timeLeft = reshuffleTime-System.currentTimeMillis();
            boolean warn = timeLeft <env.config.turnTimeoutWarningMillis;
            env.ui.setCountdown(timeLeft,warn);
        }
        else if(env.config.turnTimeoutMillis==INDICATOR_TIME){
            long timePassed = System.currentTimeMillis()-starttime;
            env.ui.setElapsed(timePassed);
        }

    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        for (int j=0 ; j<players.length ; j++) {
            synchronized (players[j].pressLock) {
                for (int k = 0; k < env.config.featureSize; k++) {

                    if (players[j].tokens.size() > 0) {
                        table.removeToken(j, players[j].tokens.remove());
                    }
                }

                players[j].numOfTokens = 0;
            }
        }
        int i = env.config.tableSize;
        while (i>0){
            if(table.slotToCard[i-1]!=null) {
                int card = table.slotToCard[i - 1];
                table.removeCard(i - 1);
                deck.add(card);
            }
            i--;
        }
        for (int j=0 ; j<players.length; j++){
            players[j].numOfTokens=0;
            players[j].tokens.clear();
        }
        
        env.ui.removeTokens();

    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int higestscore = 0;
        int numofwinners=0;
        for (int i=0 ; i<players.length ; i++){
            if (players[i].getScore()>higestscore){
                higestscore = players[i].getScore();
                numofwinners =0;
            }
            if (players[i].getScore()==higestscore) {
                numofwinners++;
            }
        }
        int[] winners = new int[numofwinners];
        int counter=0;
        for (int i=0 ; i<players.length ; i++){
            if (players[i].getScore()==higestscore){
                winners[counter]= i;
                counter++;
            }
        }
        env.ui.announceWinner(winners);
    }

    public void testPlaceCard(){
        placeCardsOnTable();
    }
    public void testRemoveCardsFrom(){
        removeCardsFromTable();
    }
    public int[] testAnnounceWinners(){
        int higestscore = 0;
        int numofwinners=0;
        for (int i=0 ; i<players.length ; i++){
            if (players[i].getScore()>higestscore){
                higestscore = players[i].getScore();
                numofwinners =0;
            }
            if (players[i].getScore()==higestscore) {
                numofwinners++;
            }
        }
        int[] winners = new int[numofwinners];
        int counter=0;
        for (int i=0 ; i<players.length ; i++){
            if (players[i].getScore()==higestscore){
                winners[counter]= i;
                counter++;
            }
        }
        return winners;

    }
}


