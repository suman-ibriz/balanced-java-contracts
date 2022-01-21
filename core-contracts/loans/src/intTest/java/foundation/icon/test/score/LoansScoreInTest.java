/*
 * Copyright 2018 ICON Foundation
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

package network.balanced.test.score;

import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.IconAmount;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import network.balanced.test.Constants;
import network.balanced.test.ResultTimeoutException;
import network.balanced.test.TransactionFailureException;
import network.balanced.test.TransactionHandler;

import network.balanced.test.contracts.base.LoansScore;
import java.io.IOException;
import java.math.BigInteger;

import static network.balanced.test.Env.LOG;

public class LoansScoreInTest extends LoansScore {

    public static LoansScore upgradeToJava(TransactionHandler txHandler, Wallet wallet, Address governaceAddress, Address scoreAddress)
    throws ResultTimeoutException, TransactionFailureException, IOException {
        LOG.info("Upgrade to New Loans");
        RpcObject params = new RpcObject.Builder()
                .put("_governance", new RpcValue(governaceAddress))
                .build();
        Score score = txHandler.deploy(wallet, getFilePath("loans"), scoreAddress, params, null);
        return new LoansScore(score);
    }

    public static LoansScore deploy(TransactionHandler txHandler, Wallet wallet, Address governaceAddress)
    throws ResultTimeoutException, TransactionFailureException, IOException {
        LOG.info("Deploy New Loans");
        RpcObject params = new RpcObject.Builder()
                .put("_governance", new RpcValue(governaceAddress))
                .build();
        Score score = txHandler.deploy(wallet, getFilePath("loans"), params);
        return new LoansScore(score);
    }

    public LoansScoreInTest(Score other) {
        super(other);
    }
}