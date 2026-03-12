package com.lowenhert.patchmanagement.core.ott;

import com.lowenhert.patchmanagement.core.ott.dto.OttAutomationTestResult;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OttAutomationTestResultService {

    private final OttAutomationTestResultRepository repository;

    public OttAutomationTestResultService(OttAutomationTestResultRepository repository) {
        this.repository = repository;
    }

    public OttAutomationTestResult save(OttAutomationTestResult result){
        return repository.save(result);
    }

    public List<OttAutomationTestResult> getAll(){
        return repository.findAll();
    }

    public OttAutomationTestResult getById(Long id){
        return repository.findById(id).orElse(null);
    }

    public List<OttAutomationTestResult> getByApp(String app){
        return repository.findByOttAppName(app);
    }

    public List<OttAutomationTestResult> getByStatus(String status){
        return repository.findByStatus(status);
    }

    public List<OttAutomationTestResult> getByDevice(String device){
        return repository.findByDeviceId(device);
    }

    public List<OttAutomationTestResult> getByAppAndStatus(String app,String status){
        return repository.findByOttAppNameAndStatus(app,status);
    }

    public void delete(Long id){
        repository.deleteById(id);
    }
}