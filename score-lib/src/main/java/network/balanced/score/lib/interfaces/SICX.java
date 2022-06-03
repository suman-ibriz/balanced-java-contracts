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

package network.balanced.score.lib.interfaces;

import network.balanced.score.lib.interfaces.addresses.AdminAddress;
import network.balanced.score.lib.interfaces.addresses.GovernanceAddress;
import network.balanced.score.lib.interfaces.addresses.OracleAddress;
import network.balanced.score.lib.interfaces.base.IRC2;
import network.balanced.score.lib.interfaces.base.IRC2BurnableInterface;
import network.balanced.score.lib.interfaces.base.IRC2MintableInterface;
import score.Address;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface SICX extends IRC2, IRC2BurnableInterface, IRC2MintableInterface, GovernanceAddress, AdminAddress, OracleAddress {

    @External(readonly = true)
    String getPeg();

    @External
    void setOracleName(String _name);

    @External(readonly = true)
    String getOracleName();

    @External
    void setMinInterval(BigInteger _interval);

    @External(readonly = true)
    BigInteger getMinInterval();

    @External(readonly = true)
    BigInteger getPriceUpdateTime();

    @External
    BigInteger priceInLoop();

    @External(readonly = true)
    BigInteger balanceOf(Address _owner);

    @External(readonly = true)
    BigInteger lastPriceInLoop();

    @EventLog(indexed = 3)
    void OraclePrice(String market, String oracle_name, Address oracle_address, BigInteger price);
}