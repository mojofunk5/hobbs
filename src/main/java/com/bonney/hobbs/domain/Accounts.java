package com.bonney.hobbs.domain;

import java.util.Optional;

public class Accounts {

    private final AccountRepository repository;
    private final AuthIdentityRepository authIdentityRepository;

    public Accounts(AccountRepository repository, AuthIdentityRepository authIdentityRepository) {
        this.repository = repository;
        this.authIdentityRepository = authIdentityRepository;
    }

    public void create(PilotId pilotId, String email) {
        EmailValidator.validate(email);
        repository.create(pilotId, email);
    }

    // Also updates the PASSWORD AuthIdentity.identifier in the same call - the pilot's login
    // identifier and their account email must never desync, which is exactly what could happen
    // before this class existed (see docs/plans/pilot-account-split.md).
    public void updateEmail(PilotId pilotId, String newEmail) {
        EmailValidator.validate(newEmail);
        repository.updateEmail(pilotId, newEmail);
        authIdentityRepository.updateIdentifier(pilotId, AuthIdentityType.PASSWORD, newEmail);
    }

    // Deleting an account means removing login capability, not the Pilot record itself - the pilot's
    // logged flight history stays attributed to the same PilotId, which reverts to unclaimed (no
    // account) exactly as if it had never been registered. Also deletes every AuthIdentity for this
    // pilot, not just disables the account: leaving a PASSWORD identity behind with no Account row
    // would let a stale hashed credential still authenticate, since Auth.login's disabled check only
    // runs after a successful identity lookup.
    public void delete(PilotId pilotId) {
        repository.delete(pilotId);
        authIdentityRepository.deleteByPilotId(pilotId);
    }

    public void disable(PilotId pilotId) {
        repository.disable(pilotId);
    }

    public void enable(PilotId pilotId) {
        repository.enable(pilotId);
    }

    public boolean isDisabled(PilotId pilotId) {
        return repository.isDisabled(pilotId);
    }

    public Optional<Account> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    public Optional<Account> get(PilotId pilotId) {
        return repository.get(pilotId);
    }
}
