/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.monitor.dump;

import bisq.common.config.Config;
import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.app.misc.AppSetupWithP2PAndDAO;
import bisq.core.dao.DaoSetup;
import bisq.core.dao.governance.ballot.BallotListService;
import bisq.core.dao.governance.blindvote.MyBlindVoteListService;
import bisq.core.dao.governance.bond.reputation.MyReputationListService;
import bisq.core.dao.governance.myvote.MyVoteListService;
import bisq.core.dao.governance.proofofburn.MyProofOfBurnListService;
import bisq.core.dao.governance.proposal.MyProposalListService;
import bisq.core.filter.FilterManager;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.monitor.dump.handlers.OffersHandler;
import bisq.monitor.dump.handlers.TradeStatisticsHandler;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.storage.P2PDataStorage;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Slf4j
public class DataDumpAppSetup extends AppSetupWithP2PAndDAO {
    private final TradeStatisticsHandler tradeStatisticsHandler;
    private final OffersHandler offersHandler;

    @Inject
    public DataDumpAppSetup(P2PService p2PService,
                            P2PDataStorage p2PDataStorage,
                            PeerManager peerManager,
                            TradeStatisticsManager tradeStatisticsManager,
                            AccountAgeWitnessService accountAgeWitnessService,
                            SignedWitnessService signedWitnessService,
                            FilterManager filterManager,
                            DaoSetup daoSetup,
                            MyVoteListService myVoteListService,
                            BallotListService ballotListService,
                            MyBlindVoteListService myBlindVoteListService,
                            MyProposalListService myProposalListService,
                            MyReputationListService myReputationListService,
                            MyProofOfBurnListService myProofOfBurnListService,
                            Preferences preferences,
                            TradeStatisticsHandler tradeStatisticsHandler,
                            OffersHandler offersHandler,
                            Config config) {
        super(p2PService,
                p2PDataStorage,
                peerManager,
                tradeStatisticsManager,
                accountAgeWitnessService,
                signedWitnessService,
                filterManager,
                daoSetup,
                myVoteListService,
                ballotListService,
                myBlindVoteListService,
                myProposalListService,
                myReputationListService,
                myProofOfBurnListService,
                preferences,
                config);
        this.tradeStatisticsHandler = tradeStatisticsHandler;
        this.offersHandler = offersHandler;
    }

    @Override
    protected void onBasicServicesInitialized() {
        super.onBasicServicesInitialized();

        tradeStatisticsHandler.onAllServicesInitialized();
        offersHandler.onAllServicesInitialized();
    }
}
