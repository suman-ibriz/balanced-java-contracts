/*
 * Copyright (c) 2022-2022 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.core.governance;

import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Payable;
import score.annotation.EventLog;
import scorex.util.ArrayList;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

import static network.balanced.score.core.governance.GovernanceConstants.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Math.pow;

import network.balanced.score.lib.interfaces.Governance;

import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.structs.PrepDelegations;

public class GovernanceImpl implements Governance {
    public final VarDB<BigInteger> launchDay = Context.newVarDB(LAUNCH_DAY, BigInteger.class);
    public final VarDB<BigInteger> launchTime = Context.newVarDB(LAUNCH_TIME, BigInteger.class);
    public final VarDB<Boolean> launched = Context.newVarDB(LAUNCHED, Boolean.class);
    public final VarDB<Address> rebalancing = Context.newVarDB(REBALANCING, Address.class);
    public final VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    public final VarDB<BigInteger> voteDuration = Context.newVarDB(VOTE_DURATION, BigInteger.class);
    public final VarDB<BigInteger> balnVoteDefinitionCriterion = Context.newVarDB(MIN_BALN, BigInteger.class);
    public final VarDB<BigInteger> bnusdVoteDefinitionFee = Context.newVarDB(DEFINITION_FEE, BigInteger.class);
    public final VarDB<BigInteger> quorum = Context.newVarDB(QUORUM, BigInteger.class);

    public GovernanceImpl() {
        if (launched.getOrDefault(null) == null) {
            launched.set(false);
        }
    }

    @External(readonly = true)
    public String name() {
        return "Balanced Governance";
    }

    @External(readonly = true)
    public BigInteger getDay() {
        BigInteger blockTime = BigInteger.valueOf(Context.getBlockTimestamp()).subtract(timeOffset.getOrDefault(BigInteger.ZERO));
        return blockTime.divide(U_SECONDS_DAY);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getVotersCount(BigInteger vote_index) {
        ProposalDB proposal = new ProposalDB(vote_index);
        return Map.of(
            "for_voters", proposal.forVotersCount.getOrDefault(BigInteger.ZERO),
            "against_voters", proposal.againstVotersCount.getOrDefault(BigInteger.ZERO)
        );
    }

    @External(readonly = true)
    public Address getContractAddress(String contract) {
        return Addresses.get(contract);
    }

    @External
    public void setVoteDuration(BigInteger duration) {
        onlyOwner();
        voteDuration.set(duration);
    }

    @External(readonly = true)
    public BigInteger getVoteDuration() {
        return voteDuration.get();
    }

    @External
    public void set_zero_hour_dev(BigInteger _hour) {
        Context.require(false);
        onlyOwner();
    }

    @External
    public void setContinuousRewardsDay(BigInteger _day) {
        onlyOwner();
        Context.call(Addresses.get("loans"), "setContinuousRewardsDay", _day);
        Context.call(Addresses.get("dex"), "setContinuousRewardsDay", _day);
        Context.call(Addresses.get("rewards"), "setContinuousRewardsDay", _day);
        Context.call(Addresses.get("dividends"), "setContinuousRewardsDay", _day);
    }

    @External
    public void setFeeProcessingInterval(BigInteger _interval) {
        onlyOwner();
        Context.call(Addresses.get("feehandler"), "setFeeProcessingInterval", _interval);
    }

    @External
    public void deleteRoute(Address _fromToken, Address _toToken) {
        onlyOwner();
        Context.call(Addresses.get("feehandler"), "deleteRoute", _fromToken, _toToken);
    }

    @External
    public void setAcceptedDividendTokens(Address[] _tokens) {
        onlyOwner();
        Context.call(Addresses.get("feehandler"), "setAcceptedDividendTokens", (Object) _tokens);
    }

    @External
    public void setRoute(Address _fromToken, Address _toToken, Address[] _path) {
        onlyOwner();
        Context.call(Addresses.get("feehandler"), "setRoute", _fromToken, _toToken, (Object) _path);
    }

    @External
    public void setQuorum(BigInteger quorum) {
        onlyOwner();
        Context.require(quorum.compareTo(BigInteger.ZERO) > 0,"Quorum must be between 0 and 100.");
        Context.require(quorum.compareTo(BigInteger.valueOf(100)) < 0,"Quorum must be between 0 and 100.");

        this.quorum.set(quorum);
    }

    @External(readonly = true)
    public BigInteger getQuorum() {
        return quorum.get();
    }

    @External
    public void setVoteDefinitionFee(BigInteger fee) {
        onlyOwner();
        bnusdVoteDefinitionFee.set(fee);
    }

    @External(readonly = true)
    public BigInteger getVoteDefinitionFee() {
        return bnusdVoteDefinitionFee.get();
    }

    @External
    public void setBalnVoteDefinitionCriterion(BigInteger percentage) {
        onlyOwner();
        Context.require(balnVoteDefinitionCriterion.get().compareTo(BigInteger.ZERO) >= 0, "Basis point must be between 0 and 10000.");
        Context.require(balnVoteDefinitionCriterion.get().compareTo(BigInteger.valueOf(10000)) <= 0, "Basis point must be between 0 and 10000.");
   
        balnVoteDefinitionCriterion.set(percentage);
    }

    @External(readonly = true)
    public BigInteger getBalnVoteDefinitionCriterion() {
        return balnVoteDefinitionCriterion.get();
    }

    @External
    public void cancelVote(BigInteger vote_index) {
        ProposalDB proposal = new ProposalDB(vote_index);
        Context.require(vote_index.compareTo(BigInteger.ONE) >= 0, "There is no proposal with index " + vote_index);
        Context.require(vote_index.compareTo(proposal.proposalsCount.get()) <= 0, "There is no proposal with index " + vote_index);
        Context.require(proposal.status.get() != ProposalStatus.STATUS[ProposalStatus.ACTIVE], "Proposal can be cancelled only from active status.");
        Context.require(Context.getCaller() == proposal.proposer.get() || 
                        Context.getCaller() == Context.getOwner(), 
                        "Only owner or proposer may call this method.");

        Context.require(proposal.startSnapshot.get().compareTo(getDay()) <= 0 &&
                        Context.getCaller() == Context.getOwner(), 
                        "Only owner can cancel a vote that has started.");

        refundVoteDefinitionFee(proposal);
        proposal.active.set(false);
        proposal.status.set(ProposalStatus.STATUS[ProposalStatus.CANCELLED]);
    }

    @External
    public void defineVote(String name, String description, BigInteger vote_start, BigInteger snapshot, String actions) {
        Context.require(description.length() <= 500, "Description must be less than or equal to 500 characters.");
        Context.require(vote_start.compareTo(getDay()) > 0, "Vote cannot start at or before the current day.");
        Context.require(vote_start.compareTo(getDay()) <= 0, "");
        Context.require(getDay().compareTo(snapshot) <= 0 && 
                        snapshot.compareTo(vote_start) < 0, 
                        "The reference snapshot must be in the range: [current_day (" + getDay() + "), " +
                        "start_day - 1 (" + vote_start.subtract(BigInteger.ONE)+ ")].");
        BigInteger voteIndex = ProposalDB.getProposalId(name);
        Context.require(voteIndex.equals(BigInteger.ZERO), "Poll name " + name + " has already been used.");
        Context.require(checkBalnVoteCriterion(Context.getCaller()), "User needs at least " + balnVoteDefinitionCriterion.get().divide(BigInteger.valueOf(100)) + "% of total baln supply staked to define a vote.");
    
  
        Context.call(Addresses.get("bnUSD"), "govTransfer",Addresses.get("daofund"), bnusdVoteDefinitionFee.get());
        JsonArray actionsParsed = Json.parse(actions).asArray();
        Context.require(actionsParsed.size() <= maxActions(), TAG + ": Only " + maxActions() + " actions are allowed");
        ProposalDB.createProposal(
                name,
                description,
                Context.getCaller(),
                quorum.get().multiply(EXA).divide(BigInteger.valueOf(100)),
                MAJORITY,
                snapshot,
                vote_start,
                vote_start.add(voteDuration.get()),
                actions,
                bnusdVoteDefinitionFee.get()
        );
    }


    @External(readonly = true)
    public int maxActions() {
        return 5;
    }

    @External(readonly = true)
    public BigInteger getProposalCount() {
        return ProposalDB.getProposalCount();
    }

    @External(readonly = true)
    public List<Object> getProposals(int batch_size, int offset) {
        int start = Math.max(1, offset);
        int end = Math.min(batch_size + start, getProposalCount().intValue());
        List<Object> proposals = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            proposals.add(checkVote(BigInteger.valueOf(i)));
        }

        return proposals;
    }

    @External
    public void castVote(BigInteger vote_index, boolean vote) {
        ProposalDB proposal = new ProposalDB(vote_index);
        Context.require(getDay().compareTo(proposal.startSnapshot.get()) < 0 ||
                        getDay().compareTo(proposal.endSnapshot.get()) >= 0 || 
                        !proposal.active.get(),
                        TAG + " :This is not an active poll.");

        Address from = Context.getCaller();
        BigInteger snapshot = proposal.voteSnapshot.get();
        Address baln = Addresses.get("baln");
        BigInteger userStaked = (BigInteger) Context.call(baln, "stakedBalanceOfAt", from, snapshot);
        BigInteger totalVote = userStaked;

        Context.require(!totalVote.equals(BigInteger.ZERO), TAG + "Balanced tokens need to be staked to cast the vote.");

        BigInteger userForVotes = proposal.forVotesOfUser.getOrDefault(from, BigInteger.ZERO);
        BigInteger userAgainstVotes = proposal.againstVotesOfUser.getOrDefault(from, BigInteger.ZERO);
        BigInteger totalForVotes = proposal.totalForVotes.get();
        BigInteger totalAgainstVotes = proposal.totalAgainstVotes.get();
        BigInteger totalForVotersCount = proposal.forVotersCount.get();
        BigInteger totalForAgainstVotersCount = proposal.againstVotersCount.get();
        BigInteger totalFor;
        BigInteger totalAgainst;
        boolean isFirstTimeVote = userForVotes.signum() == 0 && userAgainstVotes.signum() == 0;

        if (vote) {
            proposal.forVotesOfUser.set(from, totalVote);
            proposal.againstVotesOfUser.set(from, BigInteger.ZERO);
            //TODO use safemath

            totalFor = totalForVotes.add(totalVote).subtract(userForVotes);
            totalAgainst = totalAgainstVotes.subtract(userAgainstVotes);
            if (isFirstTimeVote) {
                proposal.forVotersCount.set(totalForVotersCount.add(BigInteger.ONE));
            } else if (userAgainstVotes.compareTo(BigInteger.ZERO) > 0) {
                //TODO use safemath
                proposal.againstVotersCount.set(totalForAgainstVotersCount.subtract(BigInteger.ONE));
                proposal.forVotersCount.set(totalForVotersCount.add(BigInteger.ONE));
            }
        } else {
            proposal.againstVotesOfUser.set(from, totalVote);
            proposal.forVotesOfUser.set(from, BigInteger.ZERO);
            //TODO use safemath
            totalFor = totalForVotes.subtract(userForVotes);
            totalAgainst = totalAgainstVotes.add(totalVote).subtract(userAgainstVotes);

            if (isFirstTimeVote) {
                proposal.againstVotersCount.set(totalForAgainstVotersCount.add(BigInteger.ONE));
            } else if (userForVotes.compareTo(BigInteger.ZERO) > 0) {
                //TODO use safemath
                proposal.againstVotersCount.set(totalForAgainstVotersCount.add(BigInteger.ONE));
                proposal.forVotersCount.set(totalForVotersCount.subtract(BigInteger.ONE));
            }
        }
        proposal.totalForVotes.set(totalFor);
        proposal.totalAgainstVotes.set(totalAgainstVotes);

        VoteCast(proposal.name.get(), vote, from, totalVote, totalFor, totalAgainst);
    }

    @External(readonly = true)
    public BigInteger totalBaln(BigInteger _day) {
        BigInteger stakedBaln = Context.call(BigInteger.class, Addresses.get("baln"), "totalStakedBalanceOfAt", _day);
      
        //TODD should be remove before continiuos?
        Address dexAddress = Addresses.get("dex");
        BigInteger balnFromBnusdPool = Context.call(BigInteger.class, dexAddress, "totalBalnAt", BALNBNUSD_ID, _day);
        BigInteger balnFromSICXPool = Context.call(BigInteger.class, dexAddress, "totalBalnAt", BALNSICX_ID, _day);

        return stakedBaln.add(balnFromBnusdPool).add(balnFromSICXPool);
    }

    @External
    public void evaluateVote(BigInteger vote_index) {
        Context.require(vote_index.compareTo(BigInteger.ONE) > 0 || 
                        vote_index.compareTo(ProposalDB.getProposalCount()) < 0,
                        TAG + " :There is no proposal with index " + vote_index);

        ProposalDB proposal = new ProposalDB(vote_index);
        BigInteger endSnap = proposal.endSnapshot.get();
        String actions = proposal.actions.get();
        BigInteger majority = proposal.majority.get();

        Context.require(getDay().compareTo(endSnap) >= 0, TAG + " :Voting period has not ended.");
        Context.require(proposal.active.get(), TAG + " :This proposal is not active");
       
       
        Map<String, Object> result = checkVote(vote_index);
        proposal.active.set(false);
   
        BigInteger forVotes = (BigInteger) result.get("for");
        BigInteger againstVotes = (BigInteger) result.get("against");
        if (forVotes.add(againstVotes).compareTo(proposal.quorum.get()) < 0) {
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.NO_QUORUM]);
            return;
        }

        //TODO SafeMath
        BigInteger percentageFor = EXA.subtract(majority).multiply(forVotes);
        BigInteger percentageAgainst = majority.multiply(againstVotes);
        if (percentageFor.compareTo(percentageAgainst) <= 0) {
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.DEFEATED]);
            return;
        }
        if (actions.equals("[]")){
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.SUCCEEDED]);
            _refundVoteDefinitionFee(proposal);
            return;
        }
        try {
            _executeVoteActions(proposal);
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.EXECUTED]);
        } catch (Exception e) {
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.FAILED_EXECUTION]);
        }

        _refundVoteDefinitionFee(proposal);
                
    }

    @External(readonly = true)
    public BigInteger getVoteIndex(String _name) {
        return ProposalDB.getProposalId(_name);
    }

    @External(readonly = true)
    public Map<String, Object> checkVote(BigInteger _vote_index) {
        if (_vote_index.compareTo( BigInteger.ONE) < 0 ||
            _vote_index.compareTo(ProposalDB.getProposalCount()) > 0) {
            return Map.of();
        }
        ProposalDB proposal = new ProposalDB(_vote_index);
        BigInteger totalBaln = totalBaln(proposal.voteSnapshot.get());


        BigInteger nrForVotes = BigInteger.ZERO;
        BigInteger nrAgainstVotes = BigInteger.ZERO;
        if (!totalBaln.equals(BigInteger.ZERO)) {
            nrForVotes = proposal.forVotersCount.get().divide(totalBaln).multiply(EXA);
            nrAgainstVotes = proposal.againstVotersCount.get().divide(totalBaln).multiply(EXA);
        }

        return Map.ofEntries(
            entry("id ", _vote_index),
            entry("name ", proposal.name.get()),
            entry("proposer ", proposal.proposer.get()),
            entry("description ", proposal.description.get()),
            entry("majority ", proposal.majority.get()),
            entry("vote snapshot ", proposal.voteSnapshot.get()),
            entry("start day ", proposal.startSnapshot.get()),
            entry("end day ", proposal.endSnapshot.get()),
            entry("actions ", proposal.actions.get()),
            entry("quorum ", proposal.quorum.get()),
            entry("for ", nrForVotes),
            entry("against ", nrAgainstVotes),
            entry("for_voter_count ", proposal.forVotersCount.get()),
            entry("against_voter_count ", proposal.againstVotersCount.get()),
            entry("fee_refund_status ", proposal.feeRefunded.get())

        );
    }

    @External(readonly = true)
    public Map<String, BigInteger> getVotesOfUser(BigInteger vote_index, Address user ) {
        ProposalDB proposal = new ProposalDB(vote_index);
        return Map.of(
            "for", proposal.forVotesOfUser.get(user),
            "against", proposal.againstVotesOfUser.get(user)
        );
    }

    @External(readonly = true)
    public BigInteger myVotingWeight(Address _address, BigInteger _day) {
        BigInteger stake = Context.call(BigInteger.class, Addresses.get("baln"), "stakedBalanceOfAt", _address, _day);
        return stake;
    }

    @External
    public void configureBalanced() {
        onlyOwner();
        Address loansAddress = Addresses.get("loans");
        for(Map<String, Object> asset : ASSETS) {
            Context.call(loansAddress, "addAsset",
                        ADDRESSES.get(asset.get("address")),
                        asset.get("active"),
                        asset.get("collateral")
            );
        }
    }

    @External
    public void launchBalanced() {
        onlyOwner();

        if (launched.get() ) {
            return;
        }

        launched.set(true);
        Address loans = Addresses.get("loans");
        Address dex = Addresses.get("dex");
        Address rewards = Addresses.get("rewards");
        BigInteger offset = DAY_ZERO.add(launchDay.getOrDefault(BigInteger.ZERO));
        BigInteger day = BigInteger.valueOf(Context.getBlockTimestamp()).subtract(DAY_START).divide(U_SECONDS_DAY).subtract(offset);
        launchDay.set(day);
        launchTime.set(BigInteger.valueOf(Context.getBlockTimestamp()));

        BigInteger time_delta = DAY_START.add(U_SECONDS_DAY).multiply(DAY_ZERO.add(launchDay.get()).subtract(BigInteger.ONE));
        Context.call(loans, "setTimeOffset", time_delta);
        Context.call(dex, "setTimeOffset", time_delta);
        Context.call(rewards, "setTimeOffset", time_delta);

        for (Map<String, String> source : DATA_SOURCES) {
            Context.call(rewards, "addNewDataSource", source.get("name"), Addresses.get(source.get("address")));
        }

        Context.call(rewards, "updateBalTokenDistPercentage", (Object) RECIPIENTS);
        
        balanceToggleStakingEnabled();
        Context.call(loans, "turnLoansOn");
        Context.call(dex, "turnDexOn");
    }

    @External
    @Payable
    public void createBnusdMarket() {
        onlyOwner();

        BigInteger value = Context.getValue();
        Context.require(!value.equals(BigInteger.ZERO), TAG + "ICX sent must be greater than zero.");

        Address dexAddress = Addresses.get("dex");
        Address sICXAddress = Addresses.get("sicx");
        Address bnUSDAddress = Addresses.get("bnUSD");
        Address stakedLpAddress = Addresses.get("stakedLp");
        Address stakingAddress = Addresses.get("staking");
        Address rewardsAddress = Addresses.get("rewards");
        Address loansAddress = Addresses.get("loans");

        BigInteger price = Context.call(BigInteger.class, bnUSDAddress, "priceInLoop");
        BigInteger amount = EXA.multiply(value).divide(price.multiply(BigInteger.valueOf(7)));
        Context.call(value.divide(BigInteger.valueOf(7)), stakingAddress, "stakeICX", Context.getAddress());
        Context.call(Context.getBalance(Context.getAddress()), loansAddress, "depositAndBorrow", "bnUSD", amount);
        
        BigInteger bnUSDValue =  Context.call(BigInteger.class, bnUSDAddress, "balanceOf", Context.getAddress());
        BigInteger sICXValue =  Context.call(BigInteger.class, sICXAddress, "balanceOf", Context.getAddress());

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        Context.call(bnUSDAddress, "transfer", bnUSDValue, depositData.toString().getBytes());
        Context.call(sICXAddress, "transfer", sICXValue, depositData.toString().getBytes());

        Context.call(dexAddress, "add", sICXAddress, bnUSDAddress, sICXValue, bnUSDValue);
        String name = "sICX/bnUSD";
        BigInteger pid = Context.call(BigInteger.class, dexAddress, "getPoolId", sICXAddress, bnUSDAddress);
        Context.call(dexAddress , "setMarketName", pid, name);

        Context.call(rewardsAddress, "addNewDataSource", name, dexAddress);
        Context.call(stakedLpAddress, "addPool", pid);
        DistributionPercentage[] recipients = new DistributionPercentage[] {
            createDistributionPercentage("Loans",  BigInteger.valueOf(25).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("sICX/ICX",  BigInteger.TEN.multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("Worker Tokens",  BigInteger.valueOf(20).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("Reserve Fund",  BigInteger.valueOf(5).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("DAOfund",  BigInteger.valueOf(225).multiply(pow(BigInteger.TEN,15))),
            createDistributionPercentage("sICX/bnUSD",  BigInteger.valueOf(175).multiply(pow(BigInteger.TEN,15)))
        };

        Context.call(rewardsAddress, "updateBalTokenDistPercentage", (Object) recipients);
    }

    @External
    public void createBalnMarket(BigInteger _bnUSD_amount, BigInteger _baln_amount) {
        onlyOwner();

        Address dexAddress = Addresses.get("dex");
        Address balnAddress = Addresses.get("baln");
        Address bnUSDAddress = Addresses.get("bnUSD");
        Address stakedLpAddress = Addresses.get("stakedLp");
        Address rewardsAddress = Addresses.get("rewards");
        Address loansAddress = Addresses.get("loans");

        Context.call(rewardsAddress, "claimRewards");
        Context.call(loansAddress, "depositAndBorrow", "bnUSD", _bnUSD_amount);

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        Context.call(bnUSDAddress, "transfer", _bnUSD_amount, depositData.toString().getBytes());
        Context.call(balnAddress, "transfer", _baln_amount, depositData.toString().getBytes());

        Context.call(dexAddress, "add", balnAddress, bnUSDAddress, _baln_amount, _bnUSD_amount);
        String name = "BALN/bnUSD";
        BigInteger pid = Context.call(BigInteger.class, dexAddress, "getPoolId", balnAddress, bnUSDAddress);
        Context.call(dexAddress , "setMarketName", pid, name);

        Context.call(rewardsAddress, "addNewDataSource", name, dexAddress);
        Context.call(stakedLpAddress, "addPool", pid);

        DistributionPercentage[] recipients = new DistributionPercentage[] {
            createDistributionPercentage("Loans",  BigInteger.valueOf(25).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("sICX/ICX",  BigInteger.TEN.multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("Worker Tokens",  BigInteger.valueOf(20).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("Reserve Fund",  BigInteger.valueOf(5).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("DAOfund",  BigInteger.valueOf(5).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("sICX/bnUSD",  BigInteger.valueOf(175).multiply(pow(BigInteger.TEN,15))),
            createDistributionPercentage("BALN/bnUSD",  BigInteger.valueOf(175).multiply(pow(BigInteger.TEN,15)))
        };

        Context.call(rewardsAddress, "updateBalTokenDistPercentage", (Object) recipients);
    }

    @External
    public void createBalnSicxMarket(BigInteger  _sicx_amount, BigInteger _baln_amount) {
        onlyOwner();

        Address dexAddress = Addresses.get("dex");
        Address balnAddress = Addresses.get("baln");
        Address sICXAddress = Addresses.get("sicx");
        Address stakedLpAddress = Addresses.get("stakedLp");
        Address rewardsAddress = Addresses.get("rewards");

        Context.call(rewardsAddress, "claimRewards");

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        Context.call(sICXAddress, "transfer", _sicx_amount, depositData.toString().getBytes());
        Context.call(balnAddress, "transfer", _baln_amount, depositData.toString().getBytes());

        
        Context.call(dexAddress, "add", balnAddress, sICXAddress, _baln_amount, _sicx_amount);
        String name = "BALN/sICX";
        BigInteger pid = Context.call(BigInteger.class, dexAddress, "getPoolId", balnAddress, sICXAddress);
        Context.call(dexAddress , "setMarketName", pid, name);

        Context.call(rewardsAddress, "addNewDataSource", name, dexAddress);
        Context.call(stakedLpAddress, "addPool", pid);

        DistributionPercentage[] recipients = new DistributionPercentage[] {
            createDistributionPercentage("Loans",  BigInteger.valueOf(25).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("sICX/ICX",  BigInteger.TEN.multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("Worker Tokens",  BigInteger.valueOf(20).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("Reserve Fund",  BigInteger.valueOf(5).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("DAOfund",  BigInteger.valueOf(5).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("sICX/bnUSD",  BigInteger.valueOf(15).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("BALN/bnUSD",  BigInteger.valueOf(15).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("BALN/sICX",  BigInteger.valueOf(10).multiply(pow(BigInteger.TEN,16)))
        };

        Context.call(rewardsAddress, "updateBalTokenDistPercentage", (Object) recipients);
    }

    @External
    public void rebalancingSetBnusd(Address _address) {
        onlyOwner();
        Context.call(rebalancing.get(), "setBnusd", _address);
    }

    @External
    public void rebalancingSetSicx(Address _address) {
        onlyOwner();
        Context.call(rebalancing.get(), "setSicx", _address);
    }

    @External
    public void rebalancingSetDex(Address _address) {
        onlyOwner();
        Context.call(rebalancing.get(), "setDex", _address);
    }

    @External
    public void rebalancingSetLoans(Address _address) {
        onlyOwner();
        Context.call(rebalancing.get(), "setLoans", _address);
    }

    @External
    public void setLoansRebalance(Address _address) {
        onlyOwner();
        Context.call(Addresses.get("loans"), "setRebalance", _address);
    }

    @External
    public void setLoansDex(Address _address) {
        onlyOwner();
        Context.call(Addresses.get("loans"), "setDex", _address);
    }

    @External
    public void setRebalancing(Address _address) {
        onlyOwner();
        rebalancing.set(_address);
    }

    @External
    public void setRebalancingThreshold(BigInteger _value) {
        onlyOwner();
        Context.call(rebalancing.get(), "setPriceDiffThreshold", _value);

    }

    @External
    public void setAddresses(BalancedAddresses _addresses) {
        onlyOwner();
        Addresses.setAddresses(_addresses);
    }

    @External(readonly = true)
    public Map<String, Address>  getAddresses() {
        return Addresses.getAddresses();
    }

    @External
    public void setAdmins() {
        onlyOwner();
        Addresses.setAdmins();
    }

    @External
    public void setContractAddresses() {
        onlyOwner();
        Addresses.setContractAddresses();

    }

    @External
    public void toggleBalancedOn() {
        onlyOwner();
        Context.call(Addresses.get("loans"), "toggleLoansOn");
    }

    @External(readonly = true)
    public BigInteger getLaunchDay() {
        return launchDay.get();
    }

    @External(readonly = true)
    public BigInteger getLaunchTime() {
        return launchTime.get();
    }

    @External
    public void addAsset(Address _token_address, boolean _active, boolean _collateral) {
        onlyOwner();
        Address loans = Addresses.get("loans");
        Context.call(loans, "addAsset", _token_address, _active, _collateral);
        Context.call(_token_address, "setAdmin", loans);
    }

    @External
    public void toggleAssetActive(String _symbol) {
        onlyOwner();
        Context.call(Addresses.get("loans"), "toggleAssetActive", _symbol);
    }

    @External
    public void addNewDataSource(String _data_source_name, String _contract_address) {
        onlyOwner();
        Context.call(Addresses.get("rewards"), "addNewDataSource", _data_source_name, _contract_address);
    }

    @External
    public void removeDataSource(String _data_source_name) {
        onlyOwner();
        Context.call(Addresses.get("rewards"), "removeDataSource", _data_source_name);
    }

    @External
    public void updateBalTokenDistPercentage(DistributionPercentage[] _recipient_list) {
        onlyOwner();
        Context.call(Addresses.get("rewards"), "updateBalTokenDistPercentage", (Object) _recipient_list);
    }

    @External
    public void bonusDist(Address[] _addresses, BigInteger[] _amounts) {
        onlyOwner();
        Context.call(Addresses.get("rewards"), "bonusDist", (Object) _addresses, (Object) _amounts);
    }

    @External
    public void setDay(BigInteger _day) {
        onlyOwner();
        Context.call(Addresses.get("rewards"), "setDay", _day);
    }

    @External
    public void dexPermit(BigInteger _id, boolean _permission) {
        onlyOwner();
        Context.call(Addresses.get("dex"), "permit", _id, _permission);
    }

    @External
    public void dexAddQuoteCoin(Address _address) {
        onlyOwner();
        Context.call(Addresses.get("dex"), "addQuoteCoin", _address);
    }

    @External
    public void setMarketName(BigInteger _id, String _name) {
        onlyOwner();
        Context.call(Addresses.get("dex"), "setMarketName", _id, _name);
    }

    @External
    public void delegate(PrepDelegations[] _delegations) {
        onlyOwner();
        Context.call(Addresses.get("loans"), "setMarketName", (Object) _delegations);
    }

    @External
    public void balwAdminTransfer(Address _from , Address _to , BigInteger _value, byte[] _data) {
        onlyOwner();
        Context.call(Addresses.get("bwt"), "adminTransfer", _from, _to, _value, _data);
    }

    @External
    public void setbnUSD(Address _address) {
        onlyOwner();
        Context.call(Addresses.get("baln"), "setbnUSD", _address);        
    }

    @External
    public void setDividends(Address _score) {
        onlyOwner();
        Context.call(Addresses.get("baln"), "setDividends", _score);
    }

    @External
    public void balanceSetDex(Address _address) {
        onlyOwner();
        Context.call(Addresses.get("baln"), "setDex", _address);
    }

    @External
    public void balanceSetOracleName(String _name) {
        onlyOwner();
        Context.call(Addresses.get("baln"), "setOracleName", _name);
    }

    @External
    public void balanceSetMinInterval(BigInteger _interval) {
        onlyOwner();
        Context.call(Addresses.get("baln"), "setMinInterval", _interval);
    }

    @External
    public void balanceToggleStakingEnabled() {
        onlyOwner();
        Context.call(Addresses.get("baln"), "toggleStakingEnabled");
    }

    @External
    public void balanceSetMinimumStake(BigInteger _amount) {
        onlyOwner();
        Context.call(Addresses.get("baln"), "setMinimumStake", _amount);
    }

    @External
    public void balanceSetUnstakingPeriod(BigInteger _time) {
        onlyOwner();
        Context.call(Addresses.get("baln"), "setUnstakingPeriod", _time);
    }

    @External
    public void addAcceptedTokens(String _token) {
        onlyOwner();
        Address token = Address.fromString(_token);
        Context.call(Addresses.get("dividends"), "addAcceptedTokens", token);
    }
    @External
    public void setAssetOracle(String _symbol, Address _address) {
        onlyOwner();
        Map<String, Address> assetAddresses = (Map<String, Address>) Context.call(Addresses.get("loans"), "getAssetTokens");
        Context.require(assetAddresses.containsKey(_symbol), TAG + ": " + _symbol + " is not a supported asset in Balanced.");

        Address token = assetAddresses.get(_symbol);
        Context.call(token, "setOracle", _address);
    }

    @External
    public void setAssetOracleName(String _symbol, String _name) {
        onlyOwner();
        Map<String, Address> assetAddresses = (Map<String, Address>) Context.call(Addresses.get("loans"), "getAssetTokens");
        Context.require(assetAddresses.containsKey(_symbol), TAG + ": " + _symbol + " is not a supported asset in Balanced.");

        Address token = assetAddresses.get(_symbol);
        Context.call(token, "setOracleName", _name);
    }

    @External
    public void setAssetMinInterval(String _symbol, BigInteger _interval) {
        onlyOwner();
        Map<String, Address> assetAddresses = (Map<String, Address>) Context.call(Addresses.get("loans"), "getAssetTokens");
        Context.require(assetAddresses.containsKey(_symbol), TAG + ": " + _symbol + " is not a supported asset in Balanced.");

        Address token = assetAddresses.get(_symbol);
        Context.call(token, "setMinInterval", _interval);
    }

    @External
    public void bnUSDSetOracle(Address _address) {
        onlyOwner();
        Context.call(Addresses.get("bnUSD"), "setOracle", _address);
    }

    @External
    public void bnUSDSetOracleName(String _name) {
        onlyOwner();
        Context.call(Addresses.get("bnUSD"), "setOracleName", _name);
    }

    @External
    public void bnUSDSetMinInterval(BigInteger _interval) {
        onlyOwner();
        Context.call(Addresses.get("bnUSD"), "setMinInterval", _interval);
    }

    @External
    public void addUsersToActiveAddresses(BigInteger _poolId, Address[] _addressList) {
        onlyOwner();
        Context.call(Addresses.get("dex"), "addLpAddresses", _poolId, (Object) _addressList);
    }

    @External
    public void setRedemptionFee(BigInteger _fee) {
        onlyOwner();
        Context.call(Addresses.get("loans"), "setRedemptionFee", _fee);
    }

    @External
    public void setMaxRetirePercent(BigInteger _value) {
        onlyOwner();
        Context.call(Addresses.get("loans"), "setMaxRetirePercent", _value);
    }

    @External
    public void setRedeemBatchSize(BigInteger _value) {
        onlyOwner();
        Context.call(Addresses.get("loans"), "setRedeemBatchSize", _value);
    }

    @External
    public void addPoolOnStakedLp(BigInteger _id) {
        onlyOwner();
        Context.call(Addresses.get("stakedLp"), "addPool", _id);
    }

    @External
    public void setAddressesOnContract(String _contract) {
        onlyOwner();
        Addresses.setAddress(_contract);
    }

    @External
    public void setRouter(Address _router) {
        onlyOwner();
        Addresses.router.set(_router);
    }

    @External
    public void enable_fee_handler() {
        onlyOwner();
        Context.call(Addresses.get("feehandler"), "enable");
    }

    @External
    public void disable_fee_handler() {
        onlyOwner();
        Context.call(Addresses.get("feehandler"), "disable");
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {}

    @Payable
    public void fallback() {}

    private void refundVoteDefinitionFee(ProposalDB proposal) {
        if (proposal.feeRefunded.getOrDefault(false)) {
            return;
        }
        
        Context.call(Addresses.get("bnUSD"), "govTransfer", Addresses.get("daofund"), proposal.proposer.get(), proposal.fee.get());
        proposal.feeRefunded.set(true);
    }

    private boolean checkBalnVoteCriterion(Address address) {
        Address baln = Addresses.get("baln");
        BigInteger balnTotal = Context.call(BigInteger.class, baln, "totalSupply");
        BigInteger userStaked = Context.call(BigInteger.class, baln, "stakedBalanceOf", address);
        BigInteger limit = balnVoteDefinitionCriterion.get();
        BigInteger userPercentage = POINTS.multiply(userStaked).divide(balnTotal);
        return userPercentage.compareTo(limit) >= 0;
    }

    private void _refundVoteDefinitionFee(ProposalDB proposal) {
        Address bnusd = Addresses.get("bnUSD");
        Address daoFund = Addresses.get("daofund");
        Context.call(bnusd, "govTransfer", daoFund, Context.getCaller(), proposal.fee.get());
        proposal.feeRefunded.set(true);
    }

    private void _executeVoteActions(ProposalDB proposal) {
        // JsonArray actionsParsed = Json.parse(proposal.actions.get()).asArray();
    }

    public void enableDividends() {
        Context.call(Addresses.get("dividends"), "setDistributionActivationStatus", true);
    }

    public void setMiningRatio(BigInteger _value) {
        Context.call(Addresses.get("loans"), "setMiningRatio", _value);
    }

    public void setLockingRatio(BigInteger _value) {
        Context.call(Addresses.get("loans"), "setLockingRatio", _value);
    }

    public void setOriginationFee(BigInteger _fee) {
        Context.call(Addresses.get("loans"), "setOriginationFee", _fee);
    }

    public void setLiquidationRatio(BigInteger _ratio) {
        Context.call(Addresses.get("loans"), "setLiquidationRatio", _ratio);
    }

    public void setRetirementBonus(BigInteger _points) {
        Context.call(Addresses.get("loans"), "setRetirementBonus", _points);
    }

    public void setLiquidationReward(BigInteger _points) {
        Context.call(Addresses.get("loans"), "setLiquidationReward", _points);
    }

    public void setDividendsCategoryPercentage(DistributionPercentage[] _dist_list) {
        Context.call(Addresses.get("dividends"), "setDividendsCategoryPercentage", (Object) _dist_list);
    }

    public void setPoolLpFee(BigInteger _value) {
        Context.call(Addresses.get("dex"), "setPoolLpFee", _value);
    }

    public void setPoolBalnFee(BigInteger _value) {
        Context.call(Addresses.get("dex"), "setPoolBalnFee", _value);       
    }

    public void setIcxConversionFee(BigInteger _value) {
        Context.call(Addresses.get("dex"), "setIcxConversionFee", _value);
    }

    public void setIcxBalnFee(BigInteger _value) {
         Context.call(Addresses.get("dex"), "setIcxBalnFee", _value);
    }

    @EventLog(indexed = 2)
    void VoteCast(String vote_name, boolean vote, Address voter, BigInteger stake, BigInteger total_for, BigInteger total_against){}
}
