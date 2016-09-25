package com.banjocreek.translate;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class EventHandler  {

    public Map<String,String> handle(Map<String,String> event) {
        
        if (event.containsKey("type") && event.get("type").equals("url_verification")) {
            final Optional<String> maybeChallenge = Optional.ofNullable(event.get("challenge"));
            return Collections.singletonMap("challenge", maybeChallenge.orElse("not challenged"));
        } else {
            return Collections.emptyMap();
        }
        
    }
    
    
}
