package uz.ccrew.flightmanagement.service.impl;

import uz.ccrew.flightmanagement.entity.*;
import uz.ccrew.flightmanagement.repository.*;
import uz.ccrew.flightmanagement.dto.leg.LegDTO;
import uz.ccrew.flightmanagement.mapper.LegMapper;
import uz.ccrew.flightmanagement.enums.TravelClassCode;
import uz.ccrew.flightmanagement.exp.BadRequestException;
import uz.ccrew.flightmanagement.mapper.FlightScheduleMapper;
import uz.ccrew.flightmanagement.service.FlightScheduleService;
import uz.ccrew.flightmanagement.dto.reservation.TravelClassCostDTO;
import uz.ccrew.flightmanagement.dto.reservation.TravelClassSeatDTO;
import uz.ccrew.flightmanagement.dto.reservation.FlightReservationDTO;
import uz.ccrew.flightmanagement.dto.flightSchedule.FlightScheduleDTO;
import uz.ccrew.flightmanagement.dto.reservation.ReservationRequestDTO;
import uz.ccrew.flightmanagement.dto.flightSchedule.FlightScheduleReportDTO;
import uz.ccrew.flightmanagement.dto.flightSchedule.FlightScheduleCreateDTO;

import org.springframework.data.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.time.LocalDate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlightScheduleServiceImpl implements FlightScheduleService {
    private final FlightScheduleRepository flightScheduleRepository;
    private final LegRepository legRepository;
    private final FlightCostRepository flightCostRepository;
    private final ItineraryLegRepository itineraryLegRepository;
    private final AirportRepository airportRepository;
    private final TravelClassCapacityRepository travelClassCapacityRepository;
    private final FlightScheduleMapper flightScheduleMapper;
    private final LegMapper legMapper;

    @Override
    public FlightScheduleDTO addFlightSchedule(FlightScheduleCreateDTO dto) {
        Airport originAirport = airportRepository.loadById(dto.originAirportCode());
        Airport destinationAirport = airportRepository.loadById(dto.destinationAirportCode());

        if (originAirport.getAirportCode().equals(destinationAirport.getAirportCode())) {
            throw new BadRequestException("Origin airport and destination airport can not be same");
        }
        if (dto.arrivalDateTime().isBefore(dto.departureDateTime())) {
            throw new BadRequestException("Arrival time must be after departure time");
        }

        FlightSchedule flightSchedule = flightScheduleMapper.toEntity(dto);

        flightSchedule.setOriginAirport(originAirport);
        flightSchedule.setDestinationAirport(destinationAirport);

        flightScheduleRepository.save(flightSchedule);
        return flightScheduleMapper.toDTO(flightSchedule);
    }

    @Override
    public void delete(Long flightNumber) {
        FlightSchedule flightSchedule = flightScheduleRepository.loadById(flightNumber);
        flightScheduleRepository.delete(flightSchedule);
    }

    @Override
    public FlightScheduleDTO getFlightSchedule(Long flightNumber) {
        FlightSchedule flightSchedule = flightScheduleRepository.loadById(flightNumber);
        List<Leg> legs = legRepository.findAllByFlightSchedule_FlightNumber(flightNumber);

        List<LegDTO> legDTOs = legMapper.toDTOList(legs);

        return flightScheduleMapper.toDTO(flightSchedule, legDTOs);
    }

    @Override
    public Page<FlightScheduleDTO> getAllFlightSchedulesByAirportCode(String airportCode, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Page<FlightSchedule> pageObj = flightScheduleRepository.findByAirportCode(airportCode, pageable);
        List<FlightScheduleDTO> dtoList = flightScheduleMapper.toDTOList(pageObj.getContent());

        return new PageImpl<>(dtoList, pageable, pageObj.getTotalElements());
    }

    @Override
    public Page<FlightScheduleReportDTO> getOnTimeFlights(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("departureDateTime").descending());
        Page<FlightScheduleReportDTO> pageObjDelayed = flightScheduleRepository.findOnTimeFlights(pageable);

        return new PageImpl<>(pageObjDelayed.getContent(), pageable, pageObjDelayed.getTotalElements());
    }

    @Override
    public Page<FlightScheduleReportDTO> getDelayedFlights(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("departureDateTime").descending());
        Page<FlightScheduleReportDTO> pageObjDelayed = flightScheduleRepository.findDelayedFlights(pageable);

        return new PageImpl<>(pageObjDelayed.getContent(), pageable, pageObjDelayed.getTotalElements());
    }

    public List<FlightReservationDTO> getOneWayList(ReservationRequestDTO dto) {
        // Retrieve flight schedules based on the request DTO
        List<FlightSchedule> flightSchedules = flightScheduleRepository.findOneWay(dto.departureCity(), dto.arrivalCity(), dto.departureDate());

        // Prepare a list to store the flight reservation DTOs
        List<FlightReservationDTO> flightReservationList = new ArrayList<>();

        // Iterate over each flight schedule
        for (FlightSchedule flight : flightSchedules) {
            // Get the count of legs for the flight
            int legCount = legRepository.countByFlightSchedule_FlightNumber(flight.getFlightNumber());

            // Retrieve reserved seats for each travel class
            List<TravelClassSeatDTO> travelClassSeatList = itineraryLegRepository.getTravelClassReservedSeatsByFlight(flight.getFlightNumber(), legCount);
            Map<TravelClassCode, Integer> reservedSeats = travelClassSeatList.stream().collect(Collectors.toMap(TravelClassSeatDTO::getTravelClassCode, TravelClassSeatDTO::getReservedSeats));

            // Retrieve flight costs and initialize total seats map
            List<FlightCost> flightCosts = flightCostRepository.findByFlightSchedule_FlightNumberAndId_ValidFromDateLessThanEqualAndValidToDateGreaterThanEqual(
                    flight.getFlightNumber(), LocalDate.now(), LocalDate.now());

            HashMap<TravelClassCode, Integer> totalSeats = new HashMap<>();
            List<TravelClassCostDTO> travelClassCostDTOs = new ArrayList<>();

            // Process flight costs to accumulate total seats and cost DTOs
            for (FlightCost flightCost : flightCosts) {
                List<TravelClassCapacity> travelClassCapacities = travelClassCapacityRepository.findById_AircraftTypeCode(flightCost.getId().getAircraftTypeCode());

                for (TravelClassCapacity capacity : travelClassCapacities) {
                    TravelClassCode travelClassCode = capacity.getId().getTravelClassCode();
                    totalSeats.merge(travelClassCode, capacity.getSeatCapacity(), Integer::sum);
                    travelClassCostDTOs.add(new TravelClassCostDTO(travelClassCode, flightCost.getFlightCost()));
                }
            }

            // Compute available seats
            HashMap<TravelClassCode, Integer> availableSeats = new HashMap<>();
            for (Map.Entry<TravelClassCode, Integer> entry : totalSeats.entrySet()) {
                TravelClassCode travelClassCode = entry.getKey();
                int total = entry.getValue();
                int reserved = reservedSeats.getOrDefault(travelClassCode, 0);
                int available = total - reserved;
                if (available < 1) {
                    continue;
                }
                availableSeats.put(travelClassCode, available);
            }
            if (availableSeats.isEmpty()) {
                continue;
            }
            // Create FlightReservationDTO and add to the list
            FlightReservationDTO flightReservationDTO = new FlightReservationDTO(
                    flightScheduleMapper.toDTO(flight), null, travelClassCostDTOs, availableSeats);
            flightReservationList.add(flightReservationDTO);
        }

        return flightReservationList;
    }

    @Override
    public List<FlightReservationDTO> getRoundTripList(ReservationRequestDTO reservationRequestDTO) {
        List<FlightReservationDTO> flightReservationList = new ArrayList<>();

        //logic round trip
        return flightReservationList;
    }
}
