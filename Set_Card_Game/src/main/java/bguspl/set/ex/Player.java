package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayDeque;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;


    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private int [] cards;

    public ArrayDeque<Integer> tokens;

    boolean sendToDealer;
    public int numOfTokens;

    private Dealer dealer;

    final Object locker = new Object();

    public int penaltyPoint;

    public long freezeTimes;

    public boolean toFreeze;

    public Object pressLock;
    public Object waitLock;
    public Object alreadyExistsLock;

    private int point = 1;
    private int penalty= -1;
    private int nullSet= 2;
    private int beforeAnyAction = 0;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        //tokens = new ArrayDeque(3);
        toFreeze = false;
        numOfTokens = 0;
        cards = new int[env.config.featureSize+1];
        cards[env.config.featureSize]= id;
        pressLock = new Object();
        waitLock = new Object();
        sendToDealer = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        playerThread = dealer.playerThreads[id];
        
        if (!human) {
            createArtificialIntelligence();
        }
        while (!terminate) {
            notifyDealer();
            waitUntilWokeUp();
        }
        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            try {
                Thread.sleep(env.config.tableDelayMillis*10);
            } catch (InterruptedException e) {
                System.out.println(e);
            }
            while (!terminate) {
                synchronized (aiThread) {
                int rnd =(int) (Math.random() * (env.config.tableSize));
                keyPressed(rnd);
                    try {
                        while (numOfTokens == env.config.featureSize || tokens.size() == env.config.featureSize) {
                            aiThread.wait();
                        }
                    } catch (InterruptedException ignored) {
                        //System.out.println(ignored + " player131");
                    }
                }
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        terminate= true;
        playerThread.interrupt();
        if (!human){
            aiThread.interrupt();
        }
    }



    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
     public void keyPressed(int slot) {
         // TODO implement
         synchronized (pressLock) {
             if (numOfTokens == env.config.featureSize || tokens.size() == env.config.featureSize) {
                 return;
             }
             if (table.slotToCard[slot]==null){
                 return;
             }
             boolean alreadySelected = false;
             if (tokens.contains(slot)) {
                 //int size = tokens.size();
                 tokens.remove(slot);
                 alreadySelected = true;
                 env.ui.removeToken(id, slot);
                 numOfTokens--;

             }
             if (!alreadySelected) {
                 if (table.slotToCard[slot] == null) {
                     return;
                 }
                 numOfTokens++;
                 table.placeToken(id, slot);
                 env.ui.placeToken(id, slot);
                 tokens.add(slot);
                 if (numOfTokens == env.config.featureSize) {
                     sendToDealer = true;
                     for (int i=0 ; i<env.config.featureSize ; i++){
                         int currSlot = tokens.removeFirst();
                         if (table.slotToCard[currSlot]!=null) {
                             cards[i] = table.slotToCard[currSlot];
                             tokens.add(currSlot);
                         }
                     }
                     numOfTokens= tokens.size();
                     if( numOfTokens != env.config.featureSize){
                         return;
                     }
                     dealer.cards.add(cards);
                     synchronized (waitLock) {
                         try {
                             waitLock.notifyAll();
                         } catch (IllegalMonitorStateException e) {
                             System.out.println(e);
                         }
                     }
                 }
             }
         }
     }
    /**
     * This method is called when a player press a key.
     * if the player has now 3 tokens, and now the player wait until
     * the dealer's response.
     */
     public void waitUntilWokeUp(){
         if (terminate){
             return;
         }
             synchronized (waitLock){
                 try {
                     while (!sendToDealer) {
                         waitLock.wait();
                     }
                 }
                 catch (InterruptedException  e){

                     //Thread.currentThread().interrupt();
                     //System.out.println(e);

                 }
         }
         sendToDealer=false;
     }


    /**
     * This method is called when a player press a key.
     * if the player has now 3 tokens, the dealer checks if the player has set,
     * else, the dealer does nothing.
     */
    public void notifyDealer() {
        if (numOfTokens != env.config.featureSize) {
            return;
        }
            synchronized (dealer.setsLock) {
                dealer.checksets = true;
                try {
                    dealer.setsLock.notifyAll();
                } catch (IllegalMonitorStateException e) {
                    System.out.println(e + " 208 Player");
                }
            }
            try {
                while (penaltyPoint == beforeAnyAction) {
                    synchronized (playerThread) {
                        playerThread.wait();
                    }
                }
            } catch (InterruptedException e) {
                //System.out.println(e + "198 player");
            }
            if (penaltyPoint == point) {
                toFreeze = true;
                freezeTimes = System.currentTimeMillis() + env.config.pointFreezeMillis;
                point();
            }
            if (penaltyPoint == penalty) {
                toFreeze = true;
                freezeTimes = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
                penalty();
            }
            if (penaltyPoint==nullSet){
                numOfTokens = 0;
                tokens.clear();
                penaltyPoint=beforeAnyAction;
                if (!human) {
                synchronized (aiThread){
                        try {
                            aiThread.notifyAll();
                        } catch (IllegalMonitorStateException e) {
                            System.out.println(e);
                        }
                    }
                }

            }

    }


    public boolean freezePlayer() {
        int sleepTime = 300;
        if (freezeTimes >= System.currentTimeMillis()) {
            long timeLeft = freezeTimes - System.currentTimeMillis();
            env.ui.setFreeze(id, timeLeft);
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                //System.out.println(e);
            }
            return false;
        } else {
            env.ui.setFreeze(id, 0);
            return true;
        }
    }


    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        if (toFreeze) {
            if (!human) {
                synchronized (aiThread) {
                    score++;
                    env.ui.setScore(id, score);
                    //int ignored = table.countCards(); // this part is just for demonstration in the unit tests
                    while (!freezePlayer()) {
                    }
                    penaltyPoint = beforeAnyAction;
                    synchronized (pressLock) {
                        tokens.clear();
                        numOfTokens = 0;
                    }
                    try {
                        aiThread.notifyAll();
                    } catch (IllegalMonitorStateException e) {
                        System.out.println(e + "253");
                    }
                }
            } else {
                score++;
                env.ui.setScore(id, score);
                //int ignored = table.countCards(); // this part is just for demonstration in the unit tests
                while (!freezePlayer()) {
                }
                penaltyPoint = beforeAnyAction;
                synchronized (pressLock) {

                    tokens.clear();
                    numOfTokens = 0;
                }
            }
            toFreeze = false;

        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        if (toFreeze) {
            if (!human) {
                synchronized (aiThread) {
                    while (!freezePlayer()) {
                    }
                    for (int i = 0; i < env.config.featureSize; i++) {
                        //this.env.ui.removeToken(id, tokens.remove());
                        if (table.cardToSlot[cards[i]] != null) {
                            this.env.ui.removeToken(id, table.cardToSlot[cards[i]]);
                        }
                    }
                    synchronized (pressLock) {
                        numOfTokens = 0;
                        penaltyPoint = beforeAnyAction;
                        tokens.clear();
                    }
                    try {
                        aiThread.notifyAll();
                    } catch (IllegalMonitorStateException e) {
                        System.out.println(e);
                    }

                }
            }
            else{
                while (!freezePlayer()) {}
                int tokensSize = tokens.size();
                synchronized (pressLock){
                for (int i = 0; i < tokensSize; i++) {
                    this.env.ui.removeToken(id, tokens.remove());
                }
                    numOfTokens = 0;
                    penaltyPoint = beforeAnyAction;
                    tokens.clear();
                }
            }
        }

    }

    public int[] getCardsArray(){
        return cards;
    }


    public int getScore() {
        return score;
    }
}
