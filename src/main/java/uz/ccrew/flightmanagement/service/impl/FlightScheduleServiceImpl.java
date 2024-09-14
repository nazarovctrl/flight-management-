package uz.ccrew.flightmanagement.service.impl;

import uz.ccrew.flightmanagement.dto.flightSchedule.FlightScheduleCreateDTO;
import uz.ccrew.flightmanagement.dto.flightSchedule.FlightScheduleDTO;
import uz.ccrew.flightmanagement.dto.leg.LegDTO;
import uz.ccrew.flightmanagement.dto.reservation.ReservationRequestDTO;
import uz.ccrew.flightmanagement.entity.Airport;
import uz.ccrew.flightmanagement.entity.FlightSchedule;
import uz.ccrew.flightmanagement.entity.Leg;
import uz.ccrew.flightmanagement.exp.BadRequestException;
import uz.ccrew.flightmanagement.mapper.FlightScheduleMapper;
import uz.ccrew.flightmanagement.mapper.LegMapper;
import uz.ccrew.flightmanagement.repository.AirportRepository;
import uz.ccrew.flightmanagement.repository.FlightScheduleRepository;
import uz.ccrew.flightmanagement.repository.LegRepository;
import uz.ccrew.flightmanagement.service.FlightScheduleService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FlightScheduleServiceImpl implements FlightScheduleService {
    private final FlightScheduleRepository flightScheduleRepository;
    private final LegRepository legRepository;
    private final AirportRepository airportRepository;
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
    public Page<FlightScheduleDTO> getOneWayList(ReservationRequestDTO dto, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<FlightSchedule> pageObj = flightScheduleRepository.findAll(pageable);

        List<FlightSchedule> flightList = pageObj.getContent();
        List<FlightScheduleDTO> dtoList = flightScheduleMapper.toDTOList(flightList);

        return new PageImpl<>(dtoList, pageable, pageObj.getTotalElements());
    }
}
