package com.guidinglight.nexusquant.demo.registration;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 仅可选 Demo 制品包含匿名入口；客户端不得提交任何权限、账户或资金字段。 */
@RestController
public class DemoRegistrationController {
    private final DemoRegistrationService service;
    public DemoRegistrationController(DemoRegistrationService service){this.service=service;}

    @PostMapping("/api/auth/register")
    public Map<String,String> register(@RequestBody JsonNode body,HttpServletRequest request) {
        service.rateLimit("register",request.getRemoteAddr(),30);
        fields(body,Set.of("registrationId","username","email","password","confirmPassword"));
        UUID id;
        try{id=UUID.fromString(value(body,"registrationId"));}
        catch(IllegalArgumentException failure){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"REGISTRATION_INPUT_INVALID");}
        service.register(id,value(body,"username"),value(body,"email"),value(body,"password"),value(body,"confirmPassword"));
        return Map.of("status","ACCEPTED");
    }

    @PostMapping("/api/auth/verify-email")
    public Map<String,String> verify(@RequestBody JsonNode body,HttpServletRequest request) {
        service.rateLimit("verify",request.getRemoteAddr(),60);fields(body,Set.of("token"));
        if(!service.verify(value(body,"token"))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"VERIFICATION_INVALID");
        return Map.of("status","READY");
    }

    @PostMapping("/api/auth/resend-verification")
    public Map<String,String> resend(@RequestBody JsonNode body,HttpServletRequest request) {
        service.rateLimit("resend",request.getRemoteAddr(),20);fields(body,Set.of("email"));
        service.resend(value(body,"email"));return Map.of("status","ACCEPTED");
    }

    @GetMapping("/api/demo/registration-result")
    public DemoRegistrationService.Result result(){return service.result();}

    @GetMapping("/api/demo/sim-account")
    public com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary account(){return service.account();}

    private static void fields(JsonNode body,Set<String> allowed) {
        if(body==null || !body.isObject()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"REGISTRATION_INPUT_INVALID");
        body.fieldNames().forEachRemaining(name -> {
            if(!allowed.contains(name))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"REGISTRATION_INPUT_INVALID");
        });
    }
    private static String value(JsonNode body,String name){return body.path(name).isTextual()?body.path(name).textValue():"";}
}
