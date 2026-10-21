package adliya.uz.referenceservice.service;

import adliya.uz.referenceservice.config.AzureTranslatorProperties;
import adliya.uz.referenceservice.config.InterfaceTranslationProperties;
import adliya.uz.referenceservice.dto.InterfaceTranslationOverview;
import adliya.uz.referenceservice.entity.InterfaceTranslationJob;
import adliya.uz.referenceservice.translation.ActiveLanguagesClient;
import adliya.uz.referenceservice.translation.AzureLanguageSupport;
import adliya.uz.referenceservice.translation.InterfaceMachineTranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class InterfaceTranslationOverviewService {

    private final ActiveLanguagesClient activeLanguages;
    private final InterfaceCoverageService coverageService;
    private final InterfaceMachineTranslationService machineTranslation;
    private final AzureLanguageSupport languageSupport;
    private final AzureTranslatorProperties azure;
    private final InterfaceTranslationProperties properties;

    public InterfaceTranslationOverview overview() {
        Optional<Set<String>> active = activeLanguages.activeLanguageCodes();
        Set<String> codes = new TreeSet<>(active.orElse(Set.of()));
        Map<String, InterfaceTranslationJob> jobs = machineTranslation.jobs(codes);
        List<InterfaceTranslationOverview.LanguageStatus> languages = coverageService.coverage(codes).values().stream()
                .map(coverage -> InterfaceTranslationOverview.LanguageStatus.of(coverage,
                        languageSupport.providerCode(coverage.languageCode()).orElse(null), jobs.get(coverage.languageCode())))
                .toList();
        return new InterfaceTranslationOverview(machineTranslation.automatic(), azure.configured(), properties.isEnabled(),
                active.isPresent(), languages);
    }
}
