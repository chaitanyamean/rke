package com.rke.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.rke.backend.domain.CottonLot;
import com.rke.backend.domain.CottonLotEntry;
import com.rke.backend.domain.Farmer;
import com.rke.backend.domain.Village;
import com.rke.backend.domain.enums.AuditAction;
import com.rke.backend.dto.CottonLotEntryRequest;
import com.rke.backend.dto.CottonLotEntryResponse;
import com.rke.backend.dto.CottonLotRequest;
import com.rke.backend.dto.CottonLotResponse;
import com.rke.backend.repository.CottonLotEntryRepository;
import com.rke.backend.repository.CottonLotRepository;
import com.rke.backend.repository.FarmerRepository;
import com.rke.backend.repository.VillageRepository;
import com.rke.backend.security.CurrentUserService;

import jakarta.persistence.EntityManager;

@Service
public class CottonService {

    private final CottonLotRepository cottonLotRepository;
    private final CottonLotEntryRepository cottonLotEntryRepository;
    private final FarmerRepository farmerRepository;
    private final VillageRepository villageRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;
    private final EntityManager entityManager;

    public CottonService(CottonLotRepository cottonLotRepository,
                         CottonLotEntryRepository cottonLotEntryRepository,
                         FarmerRepository farmerRepository,
                         VillageRepository villageRepository,
                         AuditService auditService,
                         CurrentUserService currentUserService,
                         EntityManager entityManager) {
        this.cottonLotRepository = cottonLotRepository;
        this.cottonLotEntryRepository = cottonLotEntryRepository;
        this.farmerRepository = farmerRepository;
        this.villageRepository = villageRepository;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
        this.entityManager = entityManager;
    }

    /**
     * Returns what the next vehicle serial number would be without incrementing the
     * sequence. Used by the frontend to pre-display the serial before the user saves.
     */
    public String peekSerialNumber() {
        UUID tenantId = currentUserService.getTenantId();
        List<?> rows = entityManager.createNativeQuery("""
                SELECT replace(replace(format_template, '{PREFIX}', COALESCE(prefix, '')),
                               '{SEQ}', lpad((current_sequence + 1)::text, padding_width, '0'))
                FROM cotton_lot_sequences
                WHERE tenant_id = :tenantId
                """)
                .setParameter("tenantId", tenantId)
                .getResultList();

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No cotton lot sequence configured for this tenant");
        }
        return (String) rows.get(0);
    }

    /**
     * Creates a cotton lot and all its entries in one transaction.
     *
     * <p>All farmers and villages are validated before any DB write so the
     * {@code @Transactional} rollback is clean on any validation failure.
     *
     * <p>Both {@code commonPrice} (the lot-level default) and each entry's actual
     * {@code price} are stored, keeping variance visible for reporting.
     */
    @Transactional
    public CottonLotResponse createCottonLot(CottonLotRequest request) {
        UUID tenantId = currentUserService.getTenantId();

        // 1. Validate all farmers + villages before any write.
        List<ValidatedEntry> validatedEntries = validateEntries(request.entries());

        // 2. Compute totals.
        BigDecimal totalQuantity = validatedEntries.stream()
                .map(ValidatedEntry::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = validatedEntries.stream()
                .map(ValidatedEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Auto-generate serial number via Postgres function (flush first so
        //    the EntityManager row lock doesn't conflict with the sequence UPDATE).
        entityManager.flush();
        entityManager.clear();
        String serial = (String) entityManager
                .createNativeQuery("SELECT next_cotton_lot_serial(:tenantId)")
                .setParameter("tenantId", tenantId)
                .getSingleResult();

        // 4. Save the lot header.
        CottonLot lot = CottonLot.builder()
                .tenantId(tenantId)
                .vehicleSerialNumber(serial)
                .vehicleRegistrationNumber(trimToNull(request.vehicleRegistrationNumber()))
                .mutaHamaliName(trimToNull(request.mutaHamaliName()))
                .commonPrice(request.commonPrice())
                .totalQuantity(totalQuantity)
                .totalAmount(totalAmount)
                .lotDate(request.lotDate())
                .build();
        cottonLotRepository.save(lot);

        // 5. Save entries.
        List<CottonLotEntry> savedEntries = new ArrayList<>(validatedEntries.size());
        for (ValidatedEntry ve : validatedEntries) {
            CottonLotEntry entry = CottonLotEntry.builder()
                    .tenantId(tenantId)
                    .cottonLotId(lot.getId())
                    .farmerId(ve.farmerId())
                    .villageId(ve.villageId())
                    .quantity(ve.quantity())
                    .price(ve.price())
                    .amount(ve.amount())
                    .build();
            savedEntries.add(cottonLotEntryRepository.save(entry));
        }

        // 6. Audit.
        auditService.record("cotton_lots", lot.getId(), AuditAction.INSERT, null,
                auditService.snapshot(lot));

        List<CottonLotEntryResponse> entryResponses = savedEntries.stream()
                .map(CottonLotEntryResponse::from)
                .toList();

        return CottonLotResponse.from(lot, entryResponses);
    }

    @Transactional(readOnly = true)
    public CottonLotResponse getLot(UUID id) {
        UUID tenantId = currentUserService.getTenantId();
        CottonLot lot = cottonLotRepository.findById(id)
                .filter(l -> l.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cotton lot not found: " + id));

        List<CottonLotEntry> entries = cottonLotEntryRepository.findByCottonLotId(lot.getId());

        Map<UUID, Farmer> farmerMap = entries.stream()
                .map(CottonLotEntry::getFarmerId).distinct()
                .collect(Collectors.toMap(fid -> fid,
                        fid -> farmerRepository.findById(fid).orElse(null), (a, b) -> a));
        Map<UUID, Village> villageMap = entries.stream()
                .map(CottonLotEntry::getVillageId).distinct()
                .collect(Collectors.toMap(vid -> vid,
                        vid -> villageRepository.findById(vid).orElse(null), (a, b) -> a));

        List<CottonLotEntryResponse> entryResponses = entries.stream()
                .map(e -> CottonLotEntryResponse.from(e,
                        farmerMap.get(e.getFarmerId()), villageMap.get(e.getVillageId())))
                .toList();
        return CottonLotResponse.from(lot, entryResponses);
    }

    @Transactional
    public CottonLotResponse updateCottonLot(UUID id, CottonLotRequest request) {
        UUID tenantId = currentUserService.getTenantId();
        CottonLot lot = cottonLotRepository.findById(id)
                .filter(l -> l.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cotton lot not found: " + id));

        List<ValidatedEntry> validatedEntries = validateEntries(request.entries());
        BigDecimal totalQuantity = validatedEntries.stream()
                .map(ValidatedEntry::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = validatedEntries.stream()
                .map(ValidatedEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Snapshot before
        Map<String, Object> before = auditService.snapshot(lot);

        lot.setVehicleRegistrationNumber(trimToNull(request.vehicleRegistrationNumber()));
        lot.setMutaHamaliName(trimToNull(request.mutaHamaliName()));
        lot.setCommonPrice(request.commonPrice());
        lot.setLotDate(request.lotDate());
        lot.setTotalQuantity(totalQuantity);
        lot.setTotalAmount(totalAmount);
        cottonLotRepository.save(lot);

        // Replace all entries
        cottonLotEntryRepository.deleteByCottonLotId(lot.getId());
        List<CottonLotEntry> savedEntries = new ArrayList<>(validatedEntries.size());
        for (ValidatedEntry ve : validatedEntries) {
            CottonLotEntry entry = CottonLotEntry.builder()
                    .tenantId(tenantId)
                    .cottonLotId(lot.getId())
                    .farmerId(ve.farmerId())
                    .villageId(ve.villageId())
                    .quantity(ve.quantity())
                    .price(ve.price())
                    .amount(ve.amount())
                    .build();
            savedEntries.add(cottonLotEntryRepository.save(entry));
        }

        Map<String, Object> after = auditService.snapshot(lot);
        auditService.record("cotton_lots", lot.getId(), AuditAction.UPDATE, before, after);

        Map<UUID, Farmer> farmerMap = savedEntries.stream()
                .map(CottonLotEntry::getFarmerId).distinct()
                .collect(Collectors.toMap(fid -> fid,
                        fid -> farmerRepository.findById(fid).orElse(null), (a, b) -> a));
        Map<UUID, Village> villageMap = savedEntries.stream()
                .map(CottonLotEntry::getVillageId).distinct()
                .collect(Collectors.toMap(vid -> vid,
                        vid -> villageRepository.findById(vid).orElse(null), (a, b) -> a));

        List<CottonLotEntryResponse> entryResponses = savedEntries.stream()
                .map(e -> CottonLotEntryResponse.from(e,
                        farmerMap.get(e.getFarmerId()), villageMap.get(e.getVillageId())))
                .toList();
        return CottonLotResponse.from(lot, entryResponses);
    }

    @Transactional(readOnly = true)
    public List<CottonLotResponse> listLots(LocalDate fromDate, LocalDate toDate) {
        List<CottonLot> lots;
        if (fromDate != null && toDate != null) {
            lots = cottonLotRepository.findByLotDateBetweenOrderByLotDateAsc(fromDate, toDate);
        } else if (fromDate != null) {
            lots = cottonLotRepository.findByLotDateGreaterThanEqualOrderByLotDateAsc(fromDate);
        } else if (toDate != null) {
            lots = cottonLotRepository.findByLotDateLessThanEqualOrderByLotDateAsc(toDate);
        } else {
            lots = cottonLotRepository.findAllByOrderByLotDateAsc();
        }

        // Collect all farmerIds and villageIds across all entries for batch lookup.
        List<CottonLotEntry> allEntries = lots.stream()
                .flatMap(lot -> cottonLotEntryRepository.findByCottonLotId(lot.getId()).stream())
                .collect(Collectors.toList());

        Map<UUID, Farmer> farmerMap = allEntries.stream()
                .map(CottonLotEntry::getFarmerId)
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> farmerRepository.findById(id).orElse(null),
                        (a, b) -> a));

        Map<UUID, Village> villageMap = allEntries.stream()
                .map(CottonLotEntry::getVillageId)
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> villageRepository.findById(id).orElse(null),
                        (a, b) -> a));

        Map<UUID, List<CottonLotEntry>> entriesByLot = allEntries.stream()
                .collect(Collectors.groupingBy(CottonLotEntry::getCottonLotId));

        return lots.stream()
                .map(lot -> {
                    List<CottonLotEntryResponse> entries = entriesByLot
                            .getOrDefault(lot.getId(), List.of()).stream()
                            .map(e -> CottonLotEntryResponse.from(
                                    e,
                                    farmerMap.get(e.getFarmerId()),
                                    villageMap.get(e.getVillageId())))
                            .toList();
                    return CottonLotResponse.from(lot, entries);
                })
                .toList();
    }

    // -------------------------------------------------------------------------

    private record ValidatedEntry(UUID farmerId, UUID villageId, BigDecimal quantity,
                                  BigDecimal price, BigDecimal amount) {}

    private List<ValidatedEntry> validateEntries(List<CottonLotEntryRequest> requests) {
        List<UUID> missingFarmers = new ArrayList<>();
        List<UUID> missingVillages = new ArrayList<>();
        List<ValidatedEntry> result = new ArrayList<>(requests.size());

        for (CottonLotEntryRequest req : requests) {
            boolean farmerOk = farmerRepository.findById(req.farmerId()).isPresent();
            boolean villageOk = villageRepository.findById(req.villageId()).isPresent();

            if (!farmerOk) missingFarmers.add(req.farmerId());
            if (!villageOk) missingVillages.add(req.villageId());

            if (farmerOk && villageOk) {
                BigDecimal amount = req.price().multiply(req.quantity());
                result.add(new ValidatedEntry(req.farmerId(), req.villageId(),
                        req.quantity(), req.price(), amount));
            }
        }

        if (!missingFarmers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Farmers not found: " + missingFarmers);
        }
        if (!missingVillages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Villages not found: " + missingVillages);
        }

        return result;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
