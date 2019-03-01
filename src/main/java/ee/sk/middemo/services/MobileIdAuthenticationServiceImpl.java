package ee.sk.middemo.services;

/*-
 * #%L
 * Mobile ID sample Java client
 * %%
 * Copyright (C) 2018 - 2019 SK ID Solutions AS
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import ee.sk.mid.AuthenticationIdentity;
import ee.sk.mid.AuthenticationResponseValidator;
import ee.sk.mid.DisplayTextFormat;
import ee.sk.mid.Language;
import ee.sk.mid.MobileIdAuthentication;
import ee.sk.mid.MobileIdAuthenticationHashToSign;
import ee.sk.mid.MobileIdAuthenticationResult;
import ee.sk.mid.MobileIdClient;
import ee.sk.mid.exception.DeliveryException;
import ee.sk.mid.exception.MidInternalErrorException;
import ee.sk.mid.exception.MidSessionTimeoutException;
import ee.sk.mid.exception.NotMidClientException;
import ee.sk.mid.exception.PhoneNotAvailableException;
import ee.sk.mid.exception.UserCancellationException;
import ee.sk.mid.rest.dao.SessionStatus;
import ee.sk.mid.rest.dao.request.AuthenticationRequest;
import ee.sk.mid.rest.dao.response.AuthenticationResponse;
import ee.sk.middemo.exception.MidOperationException;
import ee.sk.middemo.model.AuthenticationSessionInfo;
import ee.sk.middemo.model.UserRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MobileIdAuthenticationServiceImpl implements MobileIdAuthenticationService {

    Logger logger = LoggerFactory.getLogger(MobileIdSignatureServiceImpl.class);

    @Value("${mid.auth.displayText}")
    private String midAuthDisplayText;

    @Autowired
    private MobileIdClient client;

    @Override
    public AuthenticationSessionInfo startAuthentication(UserRequest userRequest) {
        MobileIdAuthenticationHashToSign authenticationHash = MobileIdAuthenticationHashToSign.generateRandomHashOfDefaultType();

        return AuthenticationSessionInfo.newBuilder()
                .withUserRequest(userRequest)
                .withAuthenticationHash(authenticationHash)
                .withVerificationCode(authenticationHash.calculateVerificationCode())
                .build();
    }

    @Override
    public AuthenticationIdentity authenticate(AuthenticationSessionInfo authenticationSessionInfo) {

        UserRequest userRequest = authenticationSessionInfo.getUserRequest();
        MobileIdAuthenticationHashToSign authenticationHash = authenticationSessionInfo.getAuthenticationHash();

        AuthenticationRequest request = AuthenticationRequest.newBuilder()
                .withPhoneNumber(userRequest.getPhoneNumber())
                .withNationalIdentityNumber(userRequest.getNationalIdentityNumber())
                .withHashToSign(authenticationHash)
                .withLanguage(Language.ENG)
                .withDisplayText(midAuthDisplayText)
                .withDisplayTextFormat(DisplayTextFormat.GSM7)
                .build();

        MobileIdAuthenticationResult authenticationResult;

        try {
            AuthenticationResponse response = client.getMobileIdConnector().authenticate(request);
            SessionStatus sessionStatus = client.getSessionStatusPoller()
                .fetchFinalAuthenticationSessionStatus(response.getSessionID());
            MobileIdAuthentication authentication = client.createMobileIdAuthentication(sessionStatus, authenticationHash);

            AuthenticationResponseValidator validator = new AuthenticationResponseValidator();
            authenticationResult = validator.validate(authentication);

        }
        catch (UserCancellationException e) {
            logger.info("User cancelled operation");
            throw new MidOperationException("User cancelled operation.");
        }
        catch (NotMidClientException e) {
            logger.info("User is not a MID client or user's certificates are revoked");
            throw new MidOperationException("User is not a MID client or user's certificates are revoked.");
        }
        catch (MidSessionTimeoutException e) {
            logger.info("User did not type in PIN or communication error.");
            throw new MidOperationException("User didn't type in PIN or communication error.");        }
        catch (PhoneNotAvailableException | DeliveryException e) {
            logger.info("Unable to reach phone/SIM card");
            throw new MidOperationException("Communication error. Unable to reach phone.");
        }
        catch (MidInternalErrorException e) {
            logger.warn("MID service returned internal error that cannot be handled locally.");
            // navigate to error page
            throw new MidOperationException("MID internal error", e);
        }

        if (!authenticationResult.isValid()) {
            throw new MidOperationException(authenticationResult.getErrors());
        }

        return authenticationResult.getAuthenticationIdentity();
    }
}
