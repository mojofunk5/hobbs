package com.bonney.hobbs.domain;

import java.util.List;
import java.util.Optional;

public class Pilots {

    private final PilotRepository repository;

    public Pilots(PilotRepository repository) {
        this.repository = repository;
    }

    public Pilot create(String name, String email) {
        NameValidator.validate(name);
        EmailValidator.validate(email);
        Pilot pilot = new Pilot(PilotId.random(), name, email);
        repository.save(pilot);
        return pilot;
    }

    public Optional<Pilot> get(PilotId id) {
        return repository.findById(id);
    }

    public List<Pilot> listAll() {
        return repository.findAllActive();
    }

    public List<PilotListRow> listActivePage(String sort, String order, int offset, int limit) {
        return repository.findAllActivePage(sort, order, offset, limit);
    }

    public int countActive() {
        return repository.countActive();
    }

    public void update(PilotId id, String name, String email) {
        NameValidator.validate(name);
        EmailValidator.validate(email);
        repository.save(new Pilot(id, name, email));
    }

    public Optional<Pilot> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    public void delete(PilotId id) {
        repository.delete(id);
    }

    public void disable(PilotId id) {
        repository.disable(id);
    }

    public void enable(PilotId id) {
        repository.enable(id);
    }

    public boolean isDisabled(PilotId id) {
        return repository.isDisabled(id);
    }
}
