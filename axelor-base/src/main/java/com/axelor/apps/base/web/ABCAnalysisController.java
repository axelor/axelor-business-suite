package com.axelor.apps.base.web;

import com.axelor.apps.base.db.ABCAnalysis;
import com.axelor.apps.base.db.ABCAnalysisClass;
import com.axelor.apps.base.db.repo.ABCAnalysisRepository;
import com.axelor.apps.base.service.ABCAnalysisService;
import com.axelor.apps.base.service.ABCAnalysisServiceImpl;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.util.List;

public class ABCAnalysisController {

    @SuppressWarnings("unchecked")
    public void runAnalysis(ActionRequest request, ActionResponse response) {
        ABCAnalysis abcAnalysis = request.getContext().asType(ABCAnalysis.class);
        try {
            Class<? extends ABCAnalysisServiceImpl> clazz = (Class<? extends  ABCAnalysisServiceImpl>) Class.forName(abcAnalysis.getTypeSelect());
            Beans.get(clazz).runAnalysis(Beans.get(ABCAnalysisRepository.class).find(abcAnalysis.getId()));
            response.setReload(true);
        } catch (ClassNotFoundException | AxelorException e ) {
            TraceBackService.trace(response, e, ResponseMessageType.ERROR);
        }
    }

    public void initABCClasses(ActionRequest request, ActionResponse response){
        List<ABCAnalysisClass> abcAnalysisClassList = Beans.get(ABCAnalysisService.class).initABCClasses();
        response.setValue("abcAnalysisClassList", abcAnalysisClassList);
    }

    public void setSequence(ActionRequest request, ActionResponse response) {
        ABCAnalysis abcAnalysis = request.getContext().asType(ABCAnalysis.class);
        Beans.get(ABCAnalysisServiceImpl.class).setSequence(abcAnalysis);
        response.setValue("abcAnalysisSeq", abcAnalysis.getAbcAnalysisSeq());
    }

    public void printReport(ActionRequest request, ActionResponse response){

        ABCAnalysis abcAnalysis = request.getContext().asType(ABCAnalysis.class);

        try{
            String name = I18n.get("ABC Analysis N°") + " " + abcAnalysis.getAbcAnalysisSeq();
            String fileLink = Beans.get(ABCAnalysisServiceImpl.class).printReport(abcAnalysis);
            response.setView(ActionView.define(name).add("html", fileLink).map());
        } catch (AxelorException e){
            response.setError(e.getMessage());
        }

    }

    public void checkClasses(ActionRequest request, ActionResponse response){
        ABCAnalysis abcAnalysis = request.getContext().asType(ABCAnalysis.class);

        try {
            Beans.get(ABCAnalysisServiceImpl.class).checkClasses(abcAnalysis);
        } catch (AxelorException e) {
            response.setError(e.getMessage());
        }
    }
}
