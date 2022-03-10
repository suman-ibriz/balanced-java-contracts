/*
 * Copyright (c) 2022 Balanced.network.
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

package score.test.staking;

import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.JsonrpcClient;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.core.interfaces.*;
import network.balanced.score.core.test.ScoreIntegrationTest;
import network.balanced.score.core.utils.Constant;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import score.Address;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class StakingIntegrationTest implements ScoreIntegrationTest {
    private final Address senderAddress = Address.fromString("hx882d4134ac6df7b4cebf75667e9762a6a2f2ff63");
    private final Address user2 = Address.fromString("hx3f01840a599da07b0f620eeae7aa9c574169a4be");
    private final Address stakingAddress = Address.fromString("cx916796da1d86b9fc9d7fe1697239c2d5ca1c4cb1");


    DefaultScoreClient stakingClient = DefaultScoreClient.of(System.getProperties());
    JsonrpcClient rpcClient = new JsonrpcClient("https://berlin.net.solidwallet.io/api/v3");


    @ScoreClient
    StakingInterface stakingManagementScore = new StakingInterfaceScoreClient(stakingClient);

    Wallet tester = DefaultScoreClient.wallet("tester.", System.getProperties());


    DefaultScoreClient clientWithTester = new DefaultScoreClient(client.endpoint(), client._nid(), tester,
            client._address());

    StakingInterface scoreClientWithNewUse = new StakingInterfaceScoreClient(clientWithTester);

    Map<String, Object> params = Map.of("_admin", stakingClient._address());

    DefaultScoreClient sicxClient = DefaultScoreClient.of("sicx.", System.getProperties(), params);
    DefaultScoreClient systemClient = DefaultScoreClient.of("system.", System.getProperties(), params);

    @ScoreClient
    SystemInterface systemScore = new SystemInterfaceScoreClient(systemClient);


    @ScoreClient
    SicxInterface sicxScore = new SicxInterfaceScoreClient(sicxClient);

//    @ScoreClient(suffix = "Client")
//    Demo scoreDemo = new DemoClient(demoClient);


//    @BeforeEach
//    void beforeAll() {
//        scoreClient.setDemoAddress(demoClient._address());
//    }


    @Test
    void testName() {
        assertEquals("Staked ICX Manager", stakingManagementScore.name());
    }

    @Test
    void testSicxAddress() {
        stakingManagementScore.toggleStakingOn();
        stakingManagementScore.setSicxAddress(sicxClient._address());
        Address value = stakingManagementScore.getSicxAddress();
        assertEquals(sicxClient._address(), value);
    }

    @Test
    void checkTopPreps() {
        List<Address> topPrep = stakingManagementScore.getTopPreps();
        List<Address> prepList = stakingManagementScore.getPrepList();
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();

        BigInteger sum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            sum = sum.add(value);
        }

        assertEquals(100, topPrep.size());
        assertEquals(new BigInteger("0"), sum);
        assertEquals(100, prepList.size());
    }

    @Test
    void testStakeIcx() {
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        // stakes 50 ICX by user1
        ((StakingInterfaceScoreClient) stakingManagementScore).stakeICX(new BigInteger("100000000000000000000"), null
                , null);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();


        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                userExpectedDelegations.put(prep.toString(), Constant.DENOMINATOR);
                expectedPrepDelegations.put(prep.toString(), Constant.DENOMINATOR);
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);

//        Assertions.assertEquals(previousTotalStake.add(new BigInteger("100000000000000000000")), prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(new BigInteger("100000000000000000000")),
                stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("100000000000000000000")),
                sicxScore.totalSupply());
        assertEquals(userBalance.add(new BigInteger("100000000000000000000")),
                sicxScore.balanceOf(senderAddress));
        Map<String, Object> delegations = systemScore.getDelegation(stakingAddress);
        Map<String, BigInteger> networkDelegations = new HashMap<>();
        List<Map<String, Object>> delegationList = (List<Map<String, Object>>) delegations.get("delegations");

        for (Map<String, Object> del : delegationList) {
            String hexValue = del.get("value").toString();
            hexValue = hexValue.replace("0x", "");
            networkDelegations.put(del.get("address").toString(), new BigInteger(hexValue, 16));
        }
        assertEquals(prepDelegations, networkDelegations);

    }

    @Test
    void testSecondStakeIcx() {
        BigInteger previousTotalStake = new BigInteger("100000000000000000000");
        BigInteger previousTotalSupply = new BigInteger("100000000000000000000");
        BigInteger userBalance = new BigInteger("100000000000000000000");
        BigInteger secondUserBalance = new BigInteger("0");


        // stakes 100 ICX to user2
        ((StakingInterfaceScoreClient) stakingManagementScore).stakeICX(new BigInteger("200000000000000000000"),
                user2, null);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                userExpectedDelegations.put(prep.toString(), new BigInteger("2000000000000000000"));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3000000000000000000"));
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(user2);

        assertEquals(previousTotalStake.add(new BigInteger("200000000000000000000")), prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(new BigInteger("200000000000000000000")),
                stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("200000000000000000000")),
                sicxScore.totalSupply());
        assertEquals(userBalance, sicxScore.balanceOf(senderAddress));
        assertEquals(secondUserBalance.add(new BigInteger("200000000000000000000")),
                sicxScore.balanceOf(user2));
        Map<String, Object> delegations = systemScore.getDelegation(stakingAddress);
        Map<String, BigInteger> networkDelegations = new HashMap<>();
        List<Map<String, Object>> delegationList = (List<Map<String, Object>>) delegations.get("delegations");

        for (Map<String, Object> del : delegationList) {
            String hexValue = del.get("value").toString();
            hexValue = hexValue.replace("0x", "");
            networkDelegations.put(del.get("address").toString(), new BigInteger(hexValue, 16));
        }
        assertEquals(prepDelegations, networkDelegations);
    }


    public boolean contains(Address target, List<Address> addresses) {
        for (Address address : addresses) {
            if (address.equals(target)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void delegate() throws Exception {


        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger secondUserBalance = sicxScore.balanceOf(user2);

        PrepDelegations p = new PrepDelegations();
//        PrepDelegations p2=new PrepDelegations();
        p._address = Address.fromString("hx24791b621e1f25bbac71e2bab8294ff38294a2c6");
        p._votes_in_per = new BigInteger("100000000000000000000");
        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        // delegates to one address
        stakingManagementScore.delegate(userDelegation);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();


        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                if (prep.toString().equals("hx24791b621e1f25bbac71e2bab8294ff38294a2c6")) {
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("102000000000000000000"));
                } else {
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("2000000000000000000"));

                }
            }
        }

        userExpectedDelegations.put("hx24791b621e1f25bbac71e2bab8294ff38294a2c6", new BigInteger(
                "100000000000000000000"));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);

        assertEquals(previousTotalStake, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply, sicxScore.totalSupply());
        assertEquals(userBalance, sicxScore.balanceOf(senderAddress));
        Map<String, Object> delegations = systemScore.getDelegation(stakingAddress);
        Map<String, BigInteger> networkDelegations = new HashMap<>();
        List<Map<String, Object>> delegationList = (List<Map<String, Object>>) delegations.get("delegations");

        for (Map<String, Object> del : delegationList) {
            String hexValue = del.get("value").toString();
            hexValue = hexValue.replace("0x", "");
            networkDelegations.put(del.get("address").toString(), new BigInteger(hexValue, 16));
        }
        assertEquals(prepDelegations, networkDelegations);
    }

    @Test
    void delegateToThreePreps() throws Exception {

        PrepDelegations p = new PrepDelegations();
        PrepDelegations p2 = new PrepDelegations();
        PrepDelegations p3 = new PrepDelegations();
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger secondUserBalance = sicxScore.balanceOf(user2);

        p._address = Address.fromString("hx3c7955f918f07df3b30c45b20f829eb8b4c8f6ff");
        p._votes_in_per = new BigInteger("50000000000000000000");
        p2._address = Address.fromString("hx3a0a9137344fdb552a146033401a52f27272c362");
        p2._votes_in_per = new BigInteger("25000000000000000000");
        p3._address = Address.fromString("hx38f35eff5e5516b48a713fe3c8031c94124191f0");
        p3._votes_in_per = new BigInteger("25000000000000000000");
        PrepDelegations[] userDelegation = new PrepDelegations[]{
                p, p2, p3
        };

        // delegates to one address
        stakingManagementScore.delegate(userDelegation);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                if (prep.toString().equals("hx3a0a9137344fdb552a146033401a52f27272c362")) {
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("27000000000000000000"));
                } else if (prep.toString().equals("hx38f35eff5e5516b48a713fe3c8031c94124191f0")) {
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("27000000000000000000"));
                } else if (prep.toString().equals("hx3c7955f918f07df3b30c45b20f829eb8b4c8f6ff")) {
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("52000000000000000000"));
                } else {
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("2000000000000000000"));

                }
            }
        }

        userExpectedDelegations.put("hx3a0a9137344fdb552a146033401a52f27272c362", new BigInteger(
                "25000000000000000000"));
        userExpectedDelegations.put("hx38f35eff5e5516b48a713fe3c8031c94124191f0", new BigInteger(
                "25000000000000000000"));
        userExpectedDelegations.put("hx3c7955f918f07df3b30c45b20f829eb8b4c8f6ff", new BigInteger(
                "50000000000000000000"));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);


        assertEquals(previousTotalStake, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply, sicxScore.totalSupply());
        assertEquals(userBalance, sicxScore.balanceOf(senderAddress));
        Map<String, Object> delegations = systemScore.getDelegation(stakingAddress);
        Map<String, BigInteger> networkDelegations = new HashMap<>();
        List<Map<String, Object>> delegationList = (List<Map<String, Object>>) delegations.get("delegations");

        for (Map<String, Object> del : delegationList) {
            String hexValue = del.get("value").toString();
            hexValue = hexValue.replace("0x", "");
            networkDelegations.put(del.get("address").toString(), new BigInteger(hexValue, 16));
        }
        assertEquals(prepDelegations, networkDelegations);


    }

    @Test
    void delegateOutsideTopPrep() throws Exception {
        PrepDelegations p = new PrepDelegations();
//        PrepDelegations p2=new PrepDelegations();
        p._address = Address.fromString("hx051e14eb7d2e04fae723cd610c153742778ad5f7");
        p._votes_in_per = new BigInteger("100000000000000000000");
        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger secondUserBalance = sicxScore.balanceOf(user2);

        // delegates to one address
        stakingManagementScore.delegate(userDelegation);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("100000000000000000000"));
            }
            if (contains(prep, topPreps)) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("2000000000000000000"));
            }
        }

        userExpectedDelegations.put("hx051e14eb7d2e04fae723cd610c153742778ad5f7", new BigInteger(
                "100000000000000000000"));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);

        assertEquals(previousTotalStake, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply, sicxScore.totalSupply());
        assertEquals(userBalance, sicxScore.balanceOf(senderAddress));
        Map<String, Object> delegations = systemScore.getDelegation(stakingAddress);
        Map<String, BigInteger> networkDelegations = new HashMap<>();
        List<Map<String, Object>> delegationList = (List<Map<String, Object>>) delegations.get("delegations");

        for (Map<String, Object> del : delegationList) {
            String hexValue = del.get("value").toString();
            hexValue = hexValue.replace("0x", "");
            networkDelegations.put(del.get("address").toString(), new BigInteger(hexValue, 16));
        }
        prepDelegations.remove("hx051e14eb7d2e04fae723cd610c153742778ad5f7");
        prepDelegations.replaceAll((k, v) -> prepDelegations.get(k).add(new BigInteger("1000000000000000000")));
        assertEquals(prepDelegations, networkDelegations);
    }

    @Test
    void transferToExistingUser() {
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger secondUserBalance = sicxScore.balanceOf(user2);

        sicxScore.transfer(user2, new BigInteger("50000000000000000000"), null);

        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> user2ExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), userBalance.subtract(new BigInteger(
                        "50000000000000000000")));
            }
            if (contains(prep, topPreps)) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("2500000000000000000"));
                user2ExpectedDelegations.put(prep.toString(), new BigInteger("2500000000000000000"));
            }
        }

        userExpectedDelegations.put("hx051e14eb7d2e04fae723cd610c153742778ad5f7",
                userBalance.subtract(new BigInteger("50000000000000000000")));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        Map<String, BigInteger> user2Delegations = stakingManagementScore.getAddressDelegations(user2);

        assertEquals(previousTotalStake, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(user2Delegations, user2ExpectedDelegations);
        assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply, sicxScore.totalSupply());
        assertEquals(userBalance.subtract(new BigInteger("50000000000000000000")),
                sicxScore.balanceOf(senderAddress));
        assertEquals(secondUserBalance.add(new BigInteger("50000000000000000000")),
                sicxScore.balanceOf(user2));
    }

    @Test
    void transferToNewUser() {
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger secondUserBalance = sicxScore.balanceOf(user2);
        Address newUser = Address.fromString("hxa88f303893fa19e4d8031dd88f6b8aa993997150");
        BigInteger newUserBalance = sicxScore.balanceOf(newUser);

        sicxScore.transfer(newUser, new BigInteger("50000000000000000000"), null);

        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> user2ExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> newUserExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), userBalance.subtract(new BigInteger(
                        "50000000000000000000")));
            }
            if (contains(prep, topPreps)) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3000000000000000000"));
                user2ExpectedDelegations.put(prep.toString(), new BigInteger("2500000000000000000"));
                newUserExpectedDelegations.put(prep.toString(), new BigInteger("500000000000000000"));
            }
        }

        userExpectedDelegations.put("hx051e14eb7d2e04fae723cd610c153742778ad5f7",
                userBalance.subtract(new BigInteger("50000000000000000000")));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        Map<String, BigInteger> user2Delegations = stakingManagementScore.getAddressDelegations(user2);
        Map<String, BigInteger> newUserDelegations = stakingManagementScore.getAddressDelegations(newUser);

        assertEquals(previousTotalStake, prepDelegationsSum);
        assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply, sicxScore.totalSupply());
        assertEquals(userBalance.subtract(new BigInteger("50000000000000000000")),
                sicxScore.balanceOf(senderAddress));
        assertEquals(secondUserBalance, sicxScore.balanceOf(user2));
        assertEquals(newUserBalance.add(new BigInteger("50000000000000000000")),
                sicxScore.balanceOf(newUser));
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(user2Delegations, user2ExpectedDelegations);
        assertEquals(newUserDelegations, newUserExpectedDelegations);

    }


    @Test
    void unstakeHalf() throws Exception {
//        stakingManagementScore.toggleStakingOn();
        ((StakingInterfaceScoreClient) stakingManagementScore).stakeICX(new BigInteger("100000000000000000000"), null
                , null);
//        stakingManagementScore.toggleStakingOn();
        JSONObject data = new JSONObject();
        data.put("method", "unstake");

        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);

        sicxScore.transfer(stakingAddress, new BigInteger("50000000000000000000"), data.toString().getBytes());

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();


        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("50000000000000000000"));
            }
            if (contains(prep, topPreps)) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3000000000000000000"));
            }
        }

        userExpectedDelegations.put("hx051e14eb7d2e04fae723cd610c153742778ad5f7", new BigInteger(
                "50000000000000000000"));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        assertEquals(previousTotalStake.subtract(new BigInteger("50000000000000000000")),
                stakingManagementScore.getTotalStake());
        assertEquals(new BigInteger("50000000000000000000"), stakingManagementScore.getUnstakingAmount());
        assertEquals(previousTotalSupply.subtract(new BigInteger("50000000000000000000")),
                sicxScore.totalSupply());
        assertEquals(userBalance.subtract(new BigInteger("50000000000000000000")),
                sicxScore.balanceOf(senderAddress));


        List<Map<String, Object>> userUnstakeInfo = stakingManagementScore.getUserUnstakeInfo(senderAddress);

        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("50000000000000000000"), new BigInteger(hexValue, 16));

        List<List<Object>> unstakeInfo = stakingManagementScore.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(0);
        assertEquals(senderAddress.toString(), firstItem.get(2));
        assertEquals(senderAddress.toString(), firstItem.get(4));
        assertEquals(previousTotalStake.subtract(new BigInteger("50000000000000000000")),
                prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
    }

    @Test
    void unstakeFull() throws Exception {
        JSONObject data = new JSONObject();
        data.put("method", "unstake");
        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");

        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);

        sicxScore.transfer(stakingAddress, new BigInteger("50000000000000000000"), data.toString().getBytes());

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("0"));
            }
            if (contains(prep, topPreps)) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3000000000000000000"));
            }
        }

        userExpectedDelegations.put("hx051e14eb7d2e04fae723cd610c153742778ad5f7", new BigInteger("0"));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        assertEquals(previousTotalStake.subtract(new BigInteger("50000000000000000000")),
                stakingManagementScore.getTotalStake());
        assertEquals(new BigInteger("100000000000000000000"), stakingManagementScore.getUnstakingAmount());
        assertEquals(previousTotalSupply.subtract(new BigInteger("50000000000000000000")),
                sicxScore.totalSupply());
        assertEquals(userBalance.subtract(new BigInteger("50000000000000000000")),
                sicxScore.balanceOf(senderAddress));


        List<Map<String, Object>> userUnstakeInfo = stakingManagementScore.getUserUnstakeInfo(Address.fromString(
                "hx8119b3eebeb9f857efb3b135275ac3775cbc6664"));
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("50000000000000000000"), new BigInteger(hexValue, 16));
        List<List<Object>> unstakeInfo = stakingManagementScore.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(1);
        assertEquals(senderAddress.toString(), firstItem.get(2));
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", firstItem.get(4));
        assertEquals(new BigInteger("50000000000000000000"), new BigInteger(hexValue, 16));
        assertEquals(previousTotalStake.subtract(new BigInteger("50000000000000000000")),
                prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
    }

    @Test
    void stakeAfterUnstake() {
        stakingClient._balance((foundation.icon.jsonrpc.Address) senderAddress);
//        BigInteger previousBalance = Context.getBalance(senderAddress);
//        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
//        BigInteger previousTotalSupply = sicxScore.totalSupply();
//        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
//        ((StakingInterfaceScoreClient)stakingManagementScore).stakeICX(new BigInteger("100000000000000000000"),
//        null, null);
////        Assertions.assertEquals(previousBalance.add(new BigInteger("1000000000000000000")), Context.getBalance
// (senderAddress));
//
//        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
//        userExpectedDelegations.put("hx051e14eb7d2e04fae723cd610c153742778ad5f7", new BigInteger
//        ("101000000000000000000"));
//        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
//        Assertions.assertTrue(userDelegations.equals(userExpectedDelegations));
//
//        // get prep delegations
//        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
//        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
//        List<Address> prepList = stakingManagementScore.getPrepList();
//        List<Address> topPreps = stakingManagementScore.getTopPreps();
//
//        for (Address prep : prepList){
//            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")){
//                expectedPrepDelegations.put(prep.toString(),  new BigInteger("101000000000000000000"));
//            }
//            if (contains(prep, topPreps)) {
//                expectedPrepDelegations.put(prep.toString(), new BigInteger("3000000000000000000"));
//            }
//        }
//
//        BigInteger prepDelegationsSum = new BigInteger("0");
//        for (BigInteger value : prepDelegations.values()) {
//            prepDelegationsSum = prepDelegationsSum.add(value);
//        }
//        // get address delegations of a user
//        Assertions.assertEquals(previousTotalStake.add(new BigInteger("100000000000000000000")),
//        stakingManagementScore.getTotalStake());
//        Assertions.assertEquals(new BigInteger("0"), stakingManagementScore.getUnstakingAmount());
//        Assertions.assertEquals(previousTotalSupply.add(new BigInteger("100000000000000000000")), sicxScore
//        .totalSupply());
//        Assertions.assertEquals(userBalance.add(new BigInteger("101000000000000000000")), sicxScore.balanceOf
//        (senderAddress));
//
//
////        Map<String, Object> userUnstakeInfo =stakingManagementScore.getUserUnstakeInfo(senderAddress);
////        Assertions.assertEquals(senderAddress.toString(),  userUnstakeInfo.get("sender"));
////        Assertions.assertEquals(senderAddress.toString(),  userUnstakeInfo.get("from"));
////        String hexValue = (String) userUnstakeInfo.get("amount");
////        hexValue = hexValue.replace("0x","");
////        Assertions.assertEquals(new BigInteger("49000000000000000000"), new BigInteger(hexValue, 16));
////        List<List<Object>> unstakeInfo = stakingManagementScore.getUnstakeInfo();
////        List<Object> firstItem = unstakeInfo.get(0);
////        Assertions.assertEquals(senderAddress.toString(),  firstItem.get(2));
////        Assertions.assertEquals(senderAddress.toString(),  firstItem.get(4));
////        Assertions.assertEquals(new BigInteger("49000000000000000000"),  new BigInteger(hexValue, 16));
//        Assertions.assertEquals(previousTotalStake.add(new BigInteger("100000000000000000000")), prepDelegationsSum);
//        Assertions.assertTrue(prepDelegations.equals(expectedPrepDelegations));
    }


    @Test
    void beforeUpdate() throws Exception {
        // stakes ICX
        ((StakingInterfaceScoreClient) stakingManagementScore).stakeICX(new BigInteger("100000000000000000000"), null
                , null);
        // Delegation is changed
        PrepDelegations p = new PrepDelegations();
//        PrepDelegations p2=new PrepDelegations();
        p._address = Address.fromString("hx24791b621e1f25bbac71e2bab8294ff38294a2c6");
        p._votes_in_per = new BigInteger("100000000000000000000");
        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        stakingManagementScore.delegate(userDelegation);
        sicxScore.transfer(user2, new BigInteger("50000000000000000000"), null);
        JSONObject data = new JSONObject();
        data.put("method", "unstake");
        sicxScore.transfer(stakingAddress, new BigInteger("10000000000000000000"), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        sicxScore.transfer(stakingAddress, new BigInteger("2000000000000000000"), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");
        sicxScore.transfer(stakingAddress, new BigInteger("10000000000000000000"), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");
        sicxScore.transfer(stakingAddress, new BigInteger("10000000000000000000"), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        sicxScore.transfer(stakingAddress, new BigInteger("10000000000000000000"), data.toString().getBytes());
        ((StakingInterfaceScoreClient) stakingManagementScore).stakeICX(new BigInteger("15000000000000000000"), null,
                null);
        checkReadonlyFunctions();

    }

    void checkReadonlyFunctions() throws Exception {
        assertEquals(new BigInteger("73000000000000000000"), stakingManagementScore.getTotalStake());
        assertEquals(new BigInteger("27000000000000000000"), stakingManagementScore.getUnstakingAmount());
        assertEquals(sicxClient._address(), stakingManagementScore.getSicxAddress());
        assertEquals(new BigInteger("12000000000000000000"),
                stakingManagementScore.claimableICX(senderAddress));
        assertEquals(new BigInteger("3000000000000000000"),
                stakingManagementScore.claimableICX(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664")));
        assertEquals(new BigInteger("15000000000000000000"), stakingManagementScore.totalClaimableIcx());
        assertEquals(new BigInteger("23000000000000000000"), sicxScore.balanceOf(senderAddress));
        assertEquals(new BigInteger("50000000000000000000"), sicxScore.balanceOf(user2));

        //checks address delegation of a sender
        checkAddressDelegations(senderAddress, "hx24791b621e1f25bbac71e2bab8294ff38294a2c6", new BigInteger(
                "23000000000000000000"));
        checkFirstUserUnstakeInfo();
        checkSecondUserUnstakeInfo();
        checkUnstakeInfo();
        checkPrepDelegations("hx24791b621e1f25bbac71e2bab8294ff38294a2c6", new BigInteger("23500000000000000000"),
                new BigInteger("500000000000000000"));
    }

    void checkAddressDelegations(Address senderAddress, String prep, BigInteger delegations) {
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        userExpectedDelegations.put(prep, delegations);
        assertEquals(userDelegations, userExpectedDelegations);
    }

    void checkPrepDelegations(String delegatedAddress, BigInteger specificDelegations, BigInteger evenlyDelegation) {
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();
        BigInteger sum = new BigInteger("0");
        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                if (prep.equals(Address.fromString(delegatedAddress))) {
                    expectedPrepDelegations.put(prep.toString(), specificDelegations);
                } else {
                    expectedPrepDelegations.put(prep.toString(), evenlyDelegation);

                }
            }
        }
        assertEquals(stakingManagementScore.getPrepDelegations(), expectedPrepDelegations);

    }

    void checkFirstUserUnstakeInfo() throws Exception {
        List<Map<String, Object>> userUnstakeInfo = stakingManagementScore.getUserUnstakeInfo(senderAddress);

        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("10000000000000000000"), new BigInteger(hexValue, 16));
    }

    void checkSecondUserUnstakeInfo() throws Exception {
        List<Map<String, Object>> userUnstakeInfo = stakingManagementScore.getUserUnstakeInfo(Address.fromString(
                "hx8119b3eebeb9f857efb3b135275ac3775cbc6664"));
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("7000000000000000000"), new BigInteger(hexValue, 16));
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", userUnstakeInfo.get(1).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(1).get("from"));
        String hexVal = (String) userUnstakeInfo.get(1).get("amount");
        hexVal = hexVal.replace("0x", "");
        assertEquals(new BigInteger("10000000000000000000"), new BigInteger(hexVal, 16));
    }

    void checkUnstakeInfo() throws Exception {
        List<List<Object>> unstakeInfo = stakingManagementScore.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(0);
        assertEquals(senderAddress.toString(), firstItem.get(2));
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", firstItem.get(4));

        String value = (String) firstItem.get(1);
        value = value.replace("0x", "");
        assertEquals(new BigInteger("7000000000000000000"), new BigInteger(value, 16));
        List<Object> secondItem = unstakeInfo.get(1);
        assertEquals(senderAddress.toString(), secondItem.get(2));
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", secondItem.get(4));
        String hexVal = (String) secondItem.get(1);
        hexVal = hexVal.replace("0x", "");
        assertEquals(new BigInteger("10000000000000000000"), new BigInteger(hexVal, 16));
        List<Object> thirdtItem = unstakeInfo.get(2);
        assertEquals(senderAddress.toString(), thirdtItem.get(2));
        assertEquals(senderAddress.toString(), thirdtItem.get(4));
        String hexValue = (String) thirdtItem.get(1);
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("10000000000000000000"), new BigInteger(hexValue, 16));
    }

    @Test
    void afterUpdate() throws Exception {
        checkReadonlyFunctions();
//         stakes ICX
        ((StakingInterfaceScoreClient) stakingManagementScore).stakeICX(new BigInteger("100000000000000000000"), null
                , null);
        // Delegation is changed
        PrepDelegations p = new PrepDelegations();
//        PrepDelegations p2=new PrepDelegations();
        p._address = Address.fromString("hx3c7955f918f07df3b30c45b20f829eb8b4c8f6ff");
        p._votes_in_per = new BigInteger("100000000000000000000");
        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        stakingManagementScore.delegate(userDelegation);
        sicxScore.transfer(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664"), new BigInteger(
                "50000000000000000000"), null);
        JSONObject data = new JSONObject();
        data.put("method", "unstake");
        sicxScore.transfer(stakingAddress, new BigInteger("10000000000000000000"), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        sicxScore.transfer(stakingAddress, new BigInteger("2000000000000000000"), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");
        sicxScore.transfer(stakingAddress, new BigInteger("10000000000000000000"), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");
        sicxScore.transfer(stakingAddress, new BigInteger("10000000000000000000"), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        sicxScore.transfer(stakingAddress, new BigInteger("10000000000000000000"), data.toString().getBytes());
        ((StakingInterfaceScoreClient) stakingManagementScore).stakeICX(new BigInteger("15000000000000000000"), null,
                null);
        assertEquals(new BigInteger("146000000000000000000"), stakingManagementScore.getTotalStake());
        assertEquals(new BigInteger("27000000000000000000"), stakingManagementScore.getUnstakingAmount());
        assertEquals(sicxClient._address(), stakingManagementScore.getSicxAddress());
        assertEquals(new BigInteger("34000000000000000000"),
                stakingManagementScore.claimableICX(senderAddress));
        assertEquals(new BigInteger("23000000000000000000"),
                stakingManagementScore.claimableICX(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664")));
        assertEquals(new BigInteger("57000000000000000000"), stakingManagementScore.totalClaimableIcx());
        assertEquals(new BigInteger("46000000000000000000"), sicxScore.balanceOf(senderAddress));
        assertEquals(new BigInteger("50000000000000000000"), sicxScore.balanceOf(user2));
        assertEquals(new BigInteger("50000000000000000000"), sicxScore.balanceOf(Address.fromString(
                "hx8119b3eebeb9f857efb3b135275ac3775cbc6664")));

        checkAddressDelegations(senderAddress, "hx3c7955f918f07df3b30c45b20f829eb8b4c8f6ff", new BigInteger(
                "46000000000000000000"));
        checkFirstUserUnstakeInfo();
        checkSecondUserUnstakeInfo();
        checkUnstakeInfo();
        checkPrepDelegations("hx3c7955f918f07df3b30c45b20f829eb8b4c8f6ff", new BigInteger("47000000000000000000"),
                new BigInteger("1000000000000000000"));
    }

}