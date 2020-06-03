package uk.nhs.adaptors.oneoneone.cda.report.mapper;

import static org.hl7.fhir.dstu3.model.IdType.newRandomUuid;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import uk.nhs.connect.iucds.cda.ucr.AD;
import uk.nhs.connect.iucds.cda.ucr.PN;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01AssignedEntity;
import uk.nhs.connect.iucds.cda.ucr.TEL;

import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.HumanName;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PractitionerMapper {

    @Autowired
    HumanNameMapper humanNameMapper;

    @Autowired
    ContactPointMapper contactPointMapper;

    @Autowired
    AddressMapper addressMapper;

    /*
    Practitioner - a person with a formal responsibility in the provisioning of healthcare or related services
     */

    public Practitioner mapPractitioner(POCDMT000002UK01AssignedEntity assignedEntity) {
        Practitioner practitioner = new Practitioner();
        practitioner.setIdElement(newRandomUuid());
        practitioner.setActive(true);
        practitioner.setName(getHumanNameFromITK(assignedEntity));
        practitioner.setTelecom(getTelecomFromITK(assignedEntity));
        practitioner.setAddress(getAddressesFromITK(assignedEntity));

        return practitioner;
    }

    private List<HumanName> getHumanNameFromITK(POCDMT000002UK01AssignedEntity assignedEntity) {
        if (assignedEntity.isSetAssignedPerson()) {
            PN[] itkPersonName = assignedEntity.getAssignedPerson().getNameArray();
            return Arrays.stream(itkPersonName)
                .map(humanNameMapper::mapHumanName)
                .collect(Collectors.toList());
        } else return Collections.emptyList();
    }

    private  List<ContactPoint> getTelecomFromITK(POCDMT000002UK01AssignedEntity assignedEntity) {
        TEL[] itkTelecom = assignedEntity.getTelecomArray();
        return Arrays.stream(itkTelecom)
            .map(contactPointMapper::mapContactPoint)
            .collect(Collectors.toList());
    }

    private List<Address> getAddressesFromITK(POCDMT000002UK01AssignedEntity assignedEntity) {
        AD[] itkAddressArray = assignedEntity.getAddrArray();
        return Arrays.stream(itkAddressArray)
            .map(addressMapper::mapAddress)
            .collect(Collectors.toList());
    }

}
