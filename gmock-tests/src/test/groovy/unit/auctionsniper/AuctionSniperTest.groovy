package unit.auctionsniper

import static auctionsniper.SniperState.*

import auctionsniper.Auction
import auctionsniper.AuctionSniper
import auctionsniper.SniperListener
import auctionsniper.SniperSnapshot
import auctionsniper.SniperState
import auctionsniper.AuctionEventListener.PriceSource
import auctionsniper.UserRequestListener.Item
import org.gmock.GMockTestCase

class AuctionSniperTest extends GMockTestCase {
    private ITEM_ID = "item-id"
    private ITEM = new Item(ITEM_ID, 1234)
    Auction auction = mock(Auction)
    SniperListener sniperListener = mock(SniperListener)
    def sniper = new AuctionSniper(ITEM, auction)

    protected void setUp() {
        sniper.addSniperListener(sniperListener)
    }

    void testHasInitialStateOfJoining() {
        assert sniper.snapshot.state == JOINING
    }

    void testReportsLostWhenAuctionClosesImmediately() {
        sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 0, 0, LOST))
        play {
            sniper.auctionClosed()
        }
    }

    void testBidsHigherAndReportsBiddingWhenNewPriceArrives() {
        def price = 1001
        def increment = 25
        def bid = price + increment

        auction.bid(bid)
        sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, price, bid, BIDDING))
        play {
            sniper.currentPrice(price, increment, PriceSource.FromOtherBidder)
        }
    }

    void testDoesNotBidAndReportsLosingIfFirstPriceIsAboveStopPrice() {
        def price = 1233
        def increment = 25

        sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, price, 0, LOSING))
        play {
            sniper.currentPrice(price, increment, PriceSource.FromOtherBidder)
        }
    }

    void testDoesNotBidAndReportsLosingIfSubsequentPriceIsAboveStopPrice() {
        def bid = 123 + 45

        auction.bid(bid)
        expectSniperBidding()
        sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 2345, bid, LOSING))
        play {
            sniper.currentPrice(123, 45, PriceSource.FromOtherBidder)
            sniper.currentPrice(2345, 25, PriceSource.FromOtherBidder)
        }
    }

    void testDoesNotBidAndReportsLosingIfPriceAfterWinningIsAboveStopPrice() {
        def price = 1233
        def increment = 25

        expectSniperBidding()
        expectSniperWinning()
        int bid = 123 + 45
        auction.bid(bid)
        sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, price, bid, LOSING))
        play {
            sniper.currentPrice(123, 45, PriceSource.FromOtherBidder)
            sniper.currentPrice(168, 45, PriceSource.FromSniper)
            sniper.currentPrice(price, increment, PriceSource.FromOtherBidder)
        }

    }

    void testContinuesToBeLosingOnceStopPriceHasBeenReached() {
        final int price1 = 1233
        final int price2 = 1258

        sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, price1, 0, LOSING))
        sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, price2, 0, LOSING))
        play {
            sniper.currentPrice(price1, 25, PriceSource.FromOtherBidder)
            sniper.currentPrice(price2, 25, PriceSource.FromOtherBidder)
        }
    }

    void testReportsLostIfAuctionClosesWhenBidding() {
        expectSniperBidding()
        ignoringAuction()

        sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 123, 168, LOST))
        play {
            sniper.currentPrice(123, 45, PriceSource.FromOtherBidder)
            sniper.auctionClosed()
        }
    }

    void testReportsLostIfAuctionClosesWhenLosing() {
        expectSniperLosing()
        sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 1230, 0, LOST))
        play {
            sniper.currentPrice(1230, 456, PriceSource.FromOtherBidder)
            sniper.auctionClosed()
        }
    }

    void testReportsIsWinningWhenCurrentPriceComesFromSniper() {
        expectSniperBidding()
        ignoringAuction()
        sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 135, 135, WINNING))
        play {
            sniper.currentPrice(123, 12, PriceSource.FromOtherBidder)
            sniper.currentPrice(135, 45, PriceSource.FromSniper)
        }
    }

    void testReportsWonIfAuctionClosesWhenWinning() {
        expectSniperBidding()
        expectSniperWinning()
        ignoringAuction()
        sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 135, 135, WON))
        play {
            sniper.currentPrice(123, 12, PriceSource.FromOtherBidder)
            sniper.currentPrice(135, 45, PriceSource.FromSniper)
            sniper.auctionClosed()
        }
    }

    void testReportsFailedIfAuctionFailsWhenBidding() {
        ignoringAuction()
        expectSniperBidding()
        expectSnipperFailed()
        play {
            sniper.currentPrice(123, 45, PriceSource.FromOtherBidder)
            sniper.auctionFailed()
        }
    }

    void testReportsFailedIfAuctionFailsImmediately() {
        sniperListener.sniperStateChanged(SniperSnapshot.joining(ITEM_ID).failed())
        play {
            sniper.auctionFailed()
        }
    }

    void testReportsFailedIfAuctionFailsWhenLosing() {
        expectSniperLosing()
        expectSnipperFailed()
        play {
            sniper.currentPrice(1230, 456, PriceSource.FromOtherBidder)
            sniper.auctionFailed()
        }
    }

    void testReportsFailedIfAuctionFailsWhenWinning() {
        ignoringAuction()
        expectSniperBidding()
        expectSniperWinning()
        expectSnipperFailed()
        sniper.currentPrice(123, 12, PriceSource.FromOtherBidder)
        sniper.currentPrice(135, 45, PriceSource.FromSniper)
        sniper.auctionFailed()
    }

    private ignoringAuction() {
        auction.bid(match{ true }).stub()
    }

    private expectSnipperFailed() {
        sniperListener.sniperStateChanged(new SniperSnapshot(ITEM_ID, 00, 0, SniperState.FAILED))
    }

    private expectSniperBidding() {
        expectSniperState(BIDDING)
    }

    private expectSniperLosing() {
        expectSniperState(LOSING)
    }

    private expectSniperWinning() {
        expectSniperState(WINNING)
    }

    private expectSniperState(SniperState state) {
        sniperListener.sniperStateChanged(match { it.state == state } )
    }

}
