package ee.sk.middemo.controller;

/*-
 * #%L
 * Smart-ID sample Java client
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

import ee.sk.middemo.exception.FileUploadException;
import ee.sk.middemo.exception.MidOperationException;
import ee.sk.middemo.model.*;
import ee.sk.middemo.services.SmartIdAuthenticationService;
import ee.sk.middemo.services.SmartIdSignatureService;
import ee.sk.smartid.AuthenticationIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.Valid;

@RestController
public class SmartIdController {
    Logger logger = LoggerFactory.getLogger(SmartIdController.class);

    private SmartIdSignatureService signatureService;
    private SmartIdAuthenticationService authenticationService;

    private UserSidSession userSidSession;

    @Autowired
    public SmartIdController(SmartIdSignatureService signatureService, SmartIdAuthenticationService authenticationService, UserSidSession userSidSession) {
        this.signatureService = signatureService;
        this.authenticationService = authenticationService;
        this.userSidSession = userSidSession; // session scope, autowired
    }

    @GetMapping(value = "/")
    public ModelAndView userRequestForm() {
        return new ModelAndView("index", "userRequest", new UserRequest());
    }

    @PostMapping(value = "/signatureRequest")
    public ModelAndView sendSignatureRequest(@ModelAttribute("userRequest") UserRequest userRequest,
                                             BindingResult bindingResult, ModelMap model) {

        if (userRequest.getFile() == null || userRequest.getFile().getOriginalFilename() == null || userRequest.getFile().isEmpty()) {
            bindingResult.rejectValue("file", "error.file", "Please select a file to upload");
        }

        if (bindingResult.hasErrors()) {
            return new ModelAndView("index", "userRequest", userRequest);
        }

        SigningSessionInfo signingSessionInfo = signatureService.sendSignatureRequest(userRequest);

        userSidSession.setSigningSessionInfo(signingSessionInfo);

        model.addAttribute("signingSessionInfo", signingSessionInfo);

        return new ModelAndView("/signature", model);
    }

    @PostMapping(value = "/sign")
    public ModelAndView sign(ModelMap model) {

        SigningResult signingResult = signatureService.sign(userSidSession.getSigningSessionInfo());

        userSidSession.clearSigningSession();

        model.addAttribute("signingResult", signingResult);

        return new ModelAndView("signingResult", model);
    }

    @PostMapping(value = "/authenticationRequest")
    public ModelAndView sendAuthenticationRequest(@ModelAttribute("userRequest") @Valid UserRequest userRequest,
                                                  BindingResult bindingResult, ModelMap model) {

        if (bindingResult.hasErrors()) {
            System.out.println("Input validation error");
            return new ModelAndView("index", "userRequest", userRequest);
        }

        AuthenticationSessionInfo authenticationSessionInfo = authenticationService.startAuthentication(userRequest);
        userSidSession.setAuthenticationSessionInfo(authenticationSessionInfo);

        model.addAttribute("verificationCode", authenticationSessionInfo.getVerificationCode());

        return new ModelAndView("/authentication", model);
    }

    @PostMapping(value = "/authenticate")
    public ModelAndView authenticate(ModelMap model) {
        AuthenticationIdentity person = authenticationService.authenticate(userSidSession.getAuthenticationSessionInfo());
        model.addAttribute("person", person);

        userSidSession.clearAuthenticationSessionInfo();

        return new ModelAndView("authenticationResult", model);
    }

    @ExceptionHandler(FileUploadException.class)
    public ModelAndView handleFileUploadException(FileUploadException exception) {
        ModelMap model = new ModelMap();

        model.addAttribute("errorMessage", "File upload error");

        return new ModelAndView("midOperationError", model);
    }

    @ExceptionHandler(MidOperationException.class)
    public ModelAndView handleMidOperationException(MidOperationException exception) {
        ModelMap model = new ModelMap();

        model.addAttribute("errorMessage", exception.getMessage());

        return new ModelAndView("midOperationError", model);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleMobileIdException(Exception exception) {
        logger.warn("Generic error caught", exception);

        ModelMap model = new ModelMap();

        model.addAttribute("errorMessage", exception.getMessage());

        return new ModelAndView("error", model);
    }


}