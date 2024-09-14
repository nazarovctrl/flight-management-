package uz.ccrew.flightmanagement.service.impl;

import uz.ccrew.flightmanagement.entity.Leg;
import uz.ccrew.flightmanagement.dto.leg.LegDTO;
import uz.ccrew.flightmanagement.mapper.LegMapper;
import uz.ccrew.flightmanagement.service.LegService;
import uz.ccrew.flightmanagement.dto.leg.LegCreateDTO;
import uz.ccrew.flightmanagement.entity.FlightSchedule;
import uz.ccrew.flightmanagement.exp.BadRequestException;
import uz.ccrew.flightmanagement.repository.LegRepository;
import uz.ccrew.flightmanagement.exp.AlreadyExistException;
import uz.ccrew.flightmanagement.repository.FlightScheduleRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegServiceImpl implements LegService {
    private final LegRepository legRepository;
    private final FlightScheduleRepository flightScheduleRepository;
    private final LegMapper legMapper;

    @Override
    public LegDTO add(LegCreateDTO dto) {
        if (dto.originAirport().equals(dto.destinationAirport())) {
            throw new BadRequestException("Origin airport and destination airport can not be same");
        }
        if (legRepository.existsByFlightSchedule_FlightNumberAndOriginAirportAndDestinationAirport(dto.flightNumber(), dto.originAirport(), dto.destinationAirport())) {
            throw new AlreadyExistException("Leg with this details already exists");
        }

        FlightSchedule flightSchedule = flightScheduleRepository.loadById(dto.flightNumber());

        Leg entity = legMapper.toEntity(dto);
        entity.setFlightSchedule(flightSchedule);
        legRepository.save(entity);

        return legMapper.toDTO(entity);
    }
}
