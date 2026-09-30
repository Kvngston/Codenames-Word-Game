package com.tk.wordagents.pack;

import com.tk.wordagents.pack.PackService.BuiltInPack;
import com.tk.wordagents.pack.PackService.PackGrant;
import com.tk.wordagents.pack.PackService.PackView;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Word packs: the built-in genres, plus packs anyone can save and share by code. */
@RestController
@RequestMapping("/api/packs")
class PackController {

    record PackRequest(String name, List<String> words) {}

    private final PackService packs;

    PackController(PackService packs) {
        this.packs = packs;
    }

    @GetMapping
    List<BuiltInPack> builtIns() {
        return packs.builtIns();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PackGrant create(@RequestBody PackRequest request) {
        return packs.create(request.name(), request.words());
    }

    @GetMapping("/{code}")
    PackView get(@PathVariable String code) {
        return packs.get(code);
    }

    @PutMapping("/{code}")
    PackView update(@PathVariable String code, @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth, @RequestBody PackRequest request) {
        return packs.update(code, bearer(auth), request.name(), request.words());
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String code, @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        packs.delete(code, bearer(auth));
    }

    private static String bearer(String header) {
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
