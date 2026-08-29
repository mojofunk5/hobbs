package com.bonney.hobbs.domain;

import java.util.List;
import java.util.Optional;

public class Pilots {

    private final PilotRepository repository;

    public Pilots(PilotRepository repository) {
        this.repository = repository;
    }

    // One creation path whether the pilot ends up with an account or not - createdBy is null for
    // self-registration, or the inviting pilot's ID for an unclaimed record.
    public Pilot create(String name, PilotId createdBy) {
        NameValidator.validate(name);
        Pilot pilot = new Pilot(PilotId.random(), name, createdBy);
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

    public void updateName(PilotId id, String name) {
        NameValidator.validate(name);
        repository.updateName(id, name);
    }

    public void delete(PilotId id) {
        repository.delete(id);
    }
}
