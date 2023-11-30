package com.umc.commonplant.domain.plant.service;

import com.umc.commonplant.domain.image.service.ImageService;
import com.umc.commonplant.domain.memo.dto.MemoDto;
import com.umc.commonplant.domain.memo.service.MemoService;
import com.umc.commonplant.domain.place.entity.Place;
import com.umc.commonplant.domain.place.service.PlaceService;
import com.umc.commonplant.domain.plant.dto.PlantDto;
import com.umc.commonplant.domain.plant.entity.Plant;
import com.umc.commonplant.domain.plant.entity.PlantRepository;
import com.umc.commonplant.domain.user.entity.User;
import com.umc.commonplant.domain2.info.dto.InfoDto;
import com.umc.commonplant.domain2.info.entity.Info;
import com.umc.commonplant.domain2.info.service.InfoService;
import com.umc.commonplant.global.exception.BadRequestException;
import com.umc.commonplant.global.exception.ErrorResponseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class PlantService {
    
    private final PlantRepository plantRepository;

    private final PlaceService placeService;
    private final ImageService imageService;
    private final InfoService infoService;
    private final MemoService memoService;

    /**
     * createPlant: 식물 추가
     * @param req
     * @param plantImage
     * @return
     */
    @Transactional
    public String createPlant(User user, PlantDto.createPlantReq req, MultipartFile plantImage) {

        // 식물 종
        // String plantName = infoService.findInfo(req.getPlantName()).getName();
        String plantName = req.getPlantName();

        // 식물의 애칭
        String plantNickname = null;

        if (req.getNickname().isBlank()) {
            throw new BadRequestException(ErrorResponseStatus.NO_PLANT_NICKNAME);
        } else if (req.getNickname().length() <= 10) {
            plantNickname = req.getNickname();
        } else {
            throw new BadRequestException(ErrorResponseStatus.LONG_PLANT_NICKNAME);
        }

        // 식물 이미지
        String imgUrl = null;

        if (plantImage.getSize() > 0) {
            imgUrl = imageService.saveImage(plantImage);
        } else {
            throw new BadRequestException(ErrorResponseStatus.NO_SELECTED_PLANT_IMAGE);
        }

        // 식물 장소
        // TODO: 식물이 있는 장소 -> 장소의 코드로 검색(추후 장소의 이름으로 바뀔 수 있음)
        Place plantPlace = placeService.getPlaceByCode(req.getPlace());

        // 물주기 기간
        String waterCycle = null;
        int castedWaterCycle = 0;

        if(String.valueOf(req.getWaterCycle()).isEmpty()){
            waterCycle = infoService.findInfo(req.getPlantName()).getWater_day().toString();
            castedWaterCycle = Integer.parseInt(waterCycle);
        } else {
            waterCycle = req.getWaterCycle();

            if(waterCycle.charAt(0) == 0) {
                throw new BadRequestException(ErrorResponseStatus.REGEX_VALIDATION_ERROR);
            } else if(req.getWaterCycle().matches("^[1-9]\\d*$")){
                castedWaterCycle = Integer.parseInt(waterCycle);
            } else {
                throw new BadRequestException(ErrorResponseStatus.REGEX_VALIDATION_ERROR);
            }
        }

        // 최근에(마지막으로) 물 준 날짜
        // TODO: WateredDate -> String에서 LocalDateTime으로 변환
        String strWateredDate = req.getStrWateredDate();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");

        LocalDateTime parsedWateredDate = null;
        parsedWateredDate = LocalDate.parse(strWateredDate, dateTimeFormatter).atStartOfDay();

        Plant plant = Plant.builder()
                .place(plantPlace)
                .plantName(plantName)
                .nickname(plantNickname)
                .imgUrl(imgUrl)
                .waterCycle(castedWaterCycle)
                .wateredDate(parsedWateredDate)
                .build();

        plantRepository.save(plant);

        return req.getNickname();
    }

    /**
     * getPlant: 식물 조회
     * @param plantIdx
     * @param user
     * @return
     */
    @Transactional
    public PlantDto.getPlantRes getPlant(User user, Long plantIdx) {

        Plant plant = plantRepository.findByPlantIdx(plantIdx)
                .orElseThrow(() -> new BadRequestException(ErrorResponseStatus.NOT_FOUND_PLANT));;

        List<Info> infoResponse = infoService.getOneInfo(plant.getPlantName());

        // log.info("식물의 고유 정보는:" + infoResponse.getScientific_name() + infoResponse.getHumidity());

        // DateTimeFormatter
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String parsedCreatedDate = plant.getCreatedAt().format(dateTimeFormatter);
        // log.info("========parsedCreatedDate======== " + parsedCreatedDate);
        LocalDateTime createdDateTime = LocalDate.parse(parsedCreatedDate, dateTimeFormatter).atStartOfDay();

        String parsedCurrentDate = LocalDate.now().toString();
        // log.info("========parsedCurrentDate======== " + parsedCurrentDate);
        LocalDateTime currentDateTime = LocalDate.parse(parsedCurrentDate, dateTimeFormatter).atStartOfDay();

        // countDate: 식물이 처음 온 날
        Long countDate =  (Long) Duration.between(createdDateTime, currentDateTime).toDays() + 1;
        // remainderDate: D-Day
        Long remainderDate = getRemainderDate(plant);

        // TODO: Memo List
        List<MemoDto.GetAllMemo> getAllMemoList = memoService.getAllMemoByPlant(plantIdx);

        // log.info("getMemoList: " + getAllMemoList);

        PlantDto.getPlantRes getPlantRes = new PlantDto.getPlantRes(
                plant.getPlantName(),
                plant.getNickname(),
                plant.getPlace().getName(),
                plant.getImgUrl(),
                countDate,
                remainderDate,
                getAllMemoList,
                infoResponse.get(0).getScientificName(),
                infoResponse.get(0).getWater_day(),
                infoResponse.get(0).getSunlight(),
                infoResponse.get(0).getTemp_min(),
                infoResponse.get(0).getTemp_max(),
                infoResponse.get(0).getHumidity(),
                plant.getCreatedAt(),
                plant.getWateredDate()
        );

        return getPlantRes;
    }

    /**
     * getRemainderDate: D-Day 계산
     * @param plant
     * @return
     */
    public Long getRemainderDate(Plant plant){

        List<Info> infoResponse = infoService.getOneInfo(plant.getPlantName());

        // DateTimeFormatter
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // WateredDate: 마지막으로 물 준 날짜, CurrentDate: 오늘 날짜
        String parsedWateredDate = plant.getWateredDate().format(dateTimeFormatter);
        // log.info("========parsedWateredDate======== " + parsedWateredDate);
        LocalDateTime wateredDateTime = LocalDate.parse(parsedWateredDate, dateTimeFormatter).atStartOfDay();

        String parsedCurrentDate = LocalDate.now().toString();
        // log.info("========parsedCurrentDate======== " + parsedCurrentDate);
        LocalDateTime currentDateTime = LocalDate.parse(parsedCurrentDate, dateTimeFormatter).atStartOfDay();

        // remainderDate: D-Day
        Long remainderDate = (Long) infoResponse.get(0).getWater_day()
                - (Long) Duration.between(wateredDateTime, currentDateTime).toDays();

        // log.info(parsedWateredDate);
        // log.info(parsedCurrentDate);
        // log.info(String.valueOf(remainderDate));

        return remainderDate;
    }

    /**
     * updateWateredDate: 식물의 D-Day 업데이트
     * @param plantIdx
     * @param user
     * @return
     */
    @Transactional
    public String updateWateredDate(Long plantIdx, User user) {

        Plant plant = plantRepository.findByPlantIdx(plantIdx)
                .orElseThrow(() -> new BadRequestException(ErrorResponseStatus.NOT_FOUND_PLANT));

        // log.info(" 물주기 리셋할 식물은: " + plant.getNickname());

        // TODO: 마지막으로 물 준 날짜
        plant.setWateredDate(LocalDateTime.now());

//        getPlantInfo()의 인자는 식물 종 이름: 식물 조회할 때 식물 종 이름을 보내서 검색하면 됨
//        InfoDto.InfoResponse infoResponse = infoService.findInfo(plant.getPlantName());
//        Long resetRemainderDate = -1 * (Long) infoResponse.getWater_day();
//        plant.setRemainderDate(resetRemainderDate);

        return plantRepository.save(plant).getNickname();
    }

    /**
     * getPlantList: 한 사람이 키우는 식물 리스트 조회
     * @param user: 유저
     * @return List<PlantDto.getPlantListRes>
     */
    @Transactional
    public List<PlantDto.getPlantListRes> getPlantList(User user) {

        // TODO: 유저에 따라 장소를 불러온 후, 장소에 따라 식물을 불러오기
        List<Place> placeList = placeService.getPlaceListByUser(user);
        List<PlantDto.getPlantListRes> plantList = new ArrayList<>();

        for(Place place : placeList){
            List<Plant> plantListByPlace = plantRepository.findAllByPlace(place);

            for(Plant plant: plantListByPlace) {
                plantList.add(new PlantDto.getPlantListRes(plant));
            }
        }

        return plantList;
    }

    /**
     * getPlantListOfPlace: 같은 장소에 있는 식물 리스트 조회
     * @param placeCode: 장소 코드
     * @return List<Plant>
     */
    @Transactional
    public List<Plant> getPlantListOfPlace(String placeCode) {

        // TODO: 식물이 있는 장소 -> 장소의 코드로 검색(추후 장소의 이름으로 바뀔 수 있음)
        Place plantPlace = placeService.getPlaceByCode(placeCode);

        return plantRepository.findAllByPlace(plantPlace);
    }

    /**
     * getMyGardenPlantList: 같은 장소에 있는 식물 리스트 조회
     * @param placeCode: 장소 코드
     * @return List<PlantDto.getMyGardenPlantListRes>
     */
    @Transactional
    public List<PlantDto.getMyGardenPlantListRes> getMyGardenPlantList(String placeCode) {

        // TODO: 식물이 있는 장소 -> 장소의 코드로 검색(추후 장소의 이름으로 바뀔 수 있음)
        Place plantPlace = placeService.getPlaceByCode(placeCode);

        List<Plant> plants = plantRepository.findAllByPlace(plantPlace);

        List<PlantDto.getMyGardenPlantListRes> plantList = new ArrayList<>();

        for(Plant plant : plants){
            Long remainderDate = getRemainderDate(plant);

            String recentMemo = null;

            if(memoService.getRecentMemoByPlant(plant.getPlantIdx()) == null){
                recentMemo = "";
            } else {
                recentMemo = memoService.getRecentMemoByPlant(plant.getPlantIdx()).getContent();
            }

            plantList.add(new PlantDto.getMyGardenPlantListRes(plant, remainderDate, recentMemo));
        }
        
        // TODO: D-Day 순으로 정렬
//        1. plantList.sort(new Comparator<PlantDto.getPlantListRes>() {
//        2. plantList.sort(new Comparator<>() {
//            @Override
//            public int compare(PlantDto.getPlantListRes p1, PlantDto.getPlantListRes p2) {
//                if (p1.getRemainderDate() > p2.getRemainderDate()) {
//                    return 1;
//                } else if (p1.getRemainderDate() < p2.getRemainderDate()) {
//                    return -1;
//                }
//                return 0;
//            }
//        });
        plantList.sort((p1, p2) -> {
            if (p1.getRemainderDate() > p2.getRemainderDate()) {
                return 1;
            } else if (p1.getRemainderDate() < p2.getRemainderDate()) {
                return -1;
            }
            return 0;
        });

        return plantList;
    }

    /**
     * updatePlant: 식물 수정
     * @param plantIdx
     * @param nickname
     * @param plantImage
     * @return
     */
    @Transactional
    public String updatePlant(Long plantIdx, String nickname, MultipartFile plantImage) {

        Plant plant = plantRepository.findByPlantIdx(plantIdx)
                .orElseThrow(() -> new BadRequestException(ErrorResponseStatus.NOT_FOUND_PLANT));

        // TODO: 식물의 애칭
        String plantNickname = null;

        if (nickname.isBlank()) {
            throw new BadRequestException(ErrorResponseStatus.NO_PLANT_NICKNAME);
        } else if (nickname.length() <= 10) {
            plantNickname = nickname;
        } else {
            throw new BadRequestException(ErrorResponseStatus.LONG_PLANT_NICKNAME);
        }

        // TODO: imgUrl
        String imgUrl = null;

        if (plantImage.getSize() > 0) {
            imgUrl = plant.getImgUrl();
        } else {
            throw new BadRequestException(ErrorResponseStatus.NO_SELECTED_PLANT_IMAGE);
        }

        plant.updatePlant(imgUrl, plantNickname);

        return plantRepository.save(plant).getNickname();
    }

    /**
     * getUpdatedPlant: 식물 수정할 때 띄우는 화면
     * @param user
     * @param plantIdx
     * @return
     */
    @Transactional
    public PlantDto.updatePlantRes getUpdatedPlant(User user, Long plantIdx) {

        Plant plant = plantRepository.findByPlantIdx(plantIdx)
                .orElseThrow(() -> new BadRequestException(ErrorResponseStatus.NOT_FOUND_PLANT));

        return new PlantDto.updatePlantRes(
                plant.getNickname(),
                plant.getImgUrl()
        );
    }

    /**
     * deletePlant: 식물 삭제
     * @param user
     * @param plantIdx
     * @return
     */
    @Transactional
    public String deletePlant(User user, Long plantIdx) {

        Plant plant = plantRepository.findByPlantIdx(plantIdx)
                .orElseThrow(() -> new BadRequestException(ErrorResponseStatus.NOT_FOUND_PLANT));

        return plant.getNickname();
    }
}
