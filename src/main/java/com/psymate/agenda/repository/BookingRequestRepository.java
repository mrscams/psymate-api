package com.psymate.agenda.repository;

import com.psymate.agenda.model.BookingRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès JPA à {@code booking_request} (V15, G Q32). M14-04 ne pose que le dépôt : les lectures et
 * les purges de la prise de rendez-vous en ligne arrivent avec M14-06 et M14-07.
 */
public interface BookingRequestRepository extends JpaRepository<BookingRequest, UUID> {}
