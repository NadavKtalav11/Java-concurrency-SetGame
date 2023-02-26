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

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import bguspl.set.Config;
@ExtendWith(MockitoExtension.class)
class PlayerTest {

    Player player;
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
    private Integer[] slotToCard;
    private Integer[] cardToSlot;


    private Player[] players ;

    void assertInvariants() {
        assertTrue(player.id >= 0);
        assertTrue(player.getScore() >= 0);
    }

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, ""), ui, util);
        players =  new Player[1];

        Properties properties = new Properties();
        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys2", "85,73,79,80");
        TableTest.MockLogger logger = new TableTest.MockLogger();
        Config config = new Config(logger, properties);
        slotToCard = new Integer[81];
        cardToSlot = new Integer[12];

        table = new Table(env, slotToCard, cardToSlot);

        dealer = new Dealer(env, table,players);
        player = new Player(env, dealer, table, 0, true);
        players[0] = player;
        table.slotToCard[1]= 2;
        table.cardToSlot[2]=1;
        table.slotToCard[2]= 4;
        table.cardToSlot[4]=2;
        table.slotToCard[3]= 3;
        table.cardToSlot[3]=3;
        table.slotToCard[5]= 8;
        table.cardToSlot[8]=5;
        //for (int i=0; i<env.config.tableSize; i++){
        //   table.placeCard(1,i);
        //}
        //dealer.placeCardsOnTable();
        assertInvariants();

    }



    @AfterEach
    void tearDown() {
        assertInvariants();
    }

        @Test
        void point() {

            // force table.countCards to return 3
            //when(table.countCards()).thenReturn(3); // this part is just for demonstration

            // calculate the expected score for later
            int expectedScore = player.getScore() + 1;

            // call the method we are testing
            player.toFreeze=true;
            player.point();

            // check that the score was increased correctly
            assertEquals(expectedScore, player.getScore());

            // check that ui.setScore was called with the player's id and the correct score
            verify(ui).setScore(eq(player.id), eq(expectedScore));
        }

    @Test
    void keyPressed() {

        // at the beginning, player should have zero token on the table
        assertEquals(0,player.numOfTokens);

        // pressing on one of the slot, should increase the num of token by 1.
        player.keyPressed(1);
        assertEquals(1,player.numOfTokens); //

        // pressing on one of the slot that the player had
        // already put token on, should remove the token from the slot.
        player.keyPressed(1);
        assertEquals(0,player.numOfTokens); //

        player.keyPressed(1);
        player.keyPressed(2);
        assertEquals(2,player.numOfTokens); //
        assertEquals(2,player.tokens.size());
        assertEquals(0,dealer.cards.size());

        // with putting the third token on the table, the dealer's setsPlayer  ArrayDeque size
        // should be increased by one.
        player.keyPressed(3);
        assertEquals(3,player.tokens.size());
        assertEquals(1,dealer.cards.size());

        // the num of tokens should remain 3, since is the max num of tokens.
        player.keyPressed(5);
        assertEquals(3,player.tokens.size());

    }




}