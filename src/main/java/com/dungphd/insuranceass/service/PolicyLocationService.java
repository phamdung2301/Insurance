package com.dungphd.insuranceass.service;

import com.dungphd.insuranceass.dto.LocationDto;
import com.dungphd.insuranceass.model.Location;
import com.dungphd.insuranceass.model.Policy;

import java.util.List;

public interface PolicyLocationService {

    List<Location> getLocations(String policyIdOrNumber);

    Location getLocationById(String policyIdOrNumber, Integer locationId);

    Policy addLocation(String policyIdOrNumber, LocationDto locationDto);

    Policy updateLocation(String policyIdOrNumber, Integer locationId, LocationDto locationDto);

    Policy removeLocation(String policyIdOrNumber, Integer locationId);
}
