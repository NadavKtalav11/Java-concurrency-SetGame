package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)

class DealerTest {
    Player player1;
    @Mock
    Player player2;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Dealer dealer;
    @Mock
    private Logger logger;


    private Player[] players ;

    @BeforeEach
    void setUp() {
        Integer [] slots = new Integer[12];
        Integer [] cards = new Integer[81];

        Env env = new Env(logger, new Config(logger, ""), ui, util);
        table =new Table(env , slots, cards);
        players =  new Player[2];
        dealer = new Dealer(env, table,players);
        player1 = new Player(env, dealer, table, 1, true);
        player2 = new Player(env, dealer, table, 2, true);
        players[0] = player1;
        players[1] = player2;
        player1.toFreeze=true;
        player2.toFreeze=true;
        for (int i=0; i<12 ; i++){
            table.slotToCard[i]=i;
            table.cardToSlot[i]=i;
        }
        table.slotToCard[10]=null;

    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void run() {
    }

    @Test
    void timerLoop() {

    }

    @Test
    void terminate() {
    }

    @Test
    void shouldFinish() {
    }

    @Test
    void removeCardsFromTable() {
        table.cardToSlot[0]=0;
        table.cardToSlot[1]=1;
        table.cardToSlot[2]=2;
        table.slotToCard[0]=0;
        table.slotToCard[1]=1;
        table.slotToCard[2]=2;
        player1.keyPressed(0);
        player1.keyPressed(1);
        player1.keyPressed(2);
        //dealer.testRemoveCardsFrom();
        //assertNotEquals(0, table.slotToCard[0]);
       //  assertNotEquals(1, table.slotToCard[1]);
        //assertNotEquals(2, table.slotToCard[2]);

    }

    @Test
    void freezePlayer() {
    }

    @Test
    void placeCardsOnTable() {

        assertEquals(11,table.countCards());
        dealer.testPlaceCard();
        assertEquals(12,table.countCards());
        table.slotToCard[0]= null;
        table.slotToCard[1]=null;
        assertEquals(10,table.countCards());
    }

    @Test
    void setOnTable() {
    }

    @Test
    void sleepUntilWokenOrTimeout() {
    }

    @Test
    void updateTimerDisplay() {
    }

    @Test
    void removeAllCardsFromTable() {

    }

    @Test
    void announceWinners(){

        player1.point();
        player1.toFreeze=true;
        player1.point();
        player1.toFreeze=true;
        player1.point();
        player1.toFreeze=true;
        player1.point();
        assertEquals(4,player1.getScore());


        player2.point();
        player2.point();
        player2.toFreeze=true;
        player2.point();
        assertEquals(2,player2.getScore());

        assertEquals(0 , dealer.testAnnounceWinners()[0]);
        assertEquals(1 , dealer.testAnnounceWinners().length);
        player2.toFreeze=true;
        player2.point();
        player2.toFreeze=true;
        player2.point();
        assertEquals(4,player2.getScore());

        assertEquals(0 , dealer.testAnnounceWinners()[0]);
        assertEquals(1 , dealer.testAnnounceWinners()[1]);
        assertEquals(2 , dealer.testAnnounceWinners().length);



    }
}