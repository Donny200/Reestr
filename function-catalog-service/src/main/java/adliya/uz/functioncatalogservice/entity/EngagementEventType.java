package adliya.uz.functioncatalogservice.entity;

import java.util.EnumSet;
import java.util.Set;

public enum EngagementEventType {
    CATALOG_VIEW(false, true, true),
    SERVICE_VIEW(true, false, false),
    OFFICIAL_LINK_CLICK(true, true, false),
    PHONE_CLICK(true, true, false),
    MAP_CLICK(true, true, false),
    PRINT(true, false, false);

    public static final Set<EngagementEventType> ACTIONS = EnumSet.of(OFFICIAL_LINK_CLICK, PHONE_CLICK, MAP_CLICK, PRINT);

    private final boolean service;
    private final boolean organization;
    private final boolean catalog;

    EngagementEventType(boolean service, boolean organization, boolean catalog) {
        this.service = service;
        this.organization = organization;
        this.catalog = catalog;
    }

    public boolean acceptsService() { return service; }
    public boolean acceptsOrganization() { return organization; }
    public boolean acceptsCatalog() { return catalog; }
}
