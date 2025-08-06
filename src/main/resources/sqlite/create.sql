CREATE TABLE Caregivers (
    Username varchar(255),
    Salt BINARY(16),
    Hash BINARY(16),
    PRIMARY KEY (Username)
);

CREATE TABLE Availabilities (
    Time date,
    Username varchar(255) REFERENCES Caregivers,
    PRIMARY KEY (Time, Username)
);

CREATE TABLE Patients (
    Username varchar(255),
    Salt BINARY(16),
    Hash BINARY(16),
    PRIMARY KEY (Username)
);

CREATE TABLE Vaccines (
    Name varchar(255),
    Doses int,
    PRIMARY KEY (Name)
);

CREATE TABLE Reservations (
    ID int,
    Vaccine varchar(255),
    Patient varchar(255),
    Caregiver varchar(255),
    Time date,
    PRIMARY KEY (ID),
    FOREIGN KEY Vaccine REFERENCES Vaccines,
    FOREIGN KEY Patient REFERENCES Patients,
    FOREIGN KEY Caregiver REFERENCES Caregivers,
);