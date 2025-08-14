package scheduler.model;

import scheduler.db.ConnectionManager;
import scheduler.util.Util;
import java.sql.*;
import java.util.Arrays;

public class Patient {
    private final String username;
    private final byte[] salt;
    private final byte[] hash;

    private Patient(Patient.PatientBuilder builder) {
        this.username = builder.username;
        this.salt = builder.salt;
        this.hash = builder.hash;
    }

    private Patient(Patient.PatientGetter getter) {
        this.username = getter.username;
        this.salt = getter.salt;
        this.hash = getter.hash;
    }

    // Getters
    public String getUsername() {
        return username;
    }

    public byte[] getSalt() {
        return salt;
    }

    public byte[] getHash() {
        return hash;
    }

    public void saveToDB() throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String addPatient = "INSERT INTO Patients VALUES (? , ?, ?)";
        try {
            PreparedStatement statement = con.prepareStatement(addPatient);
            statement.setString(1, this.username);
            statement.setBytes(2, this.salt);
            statement.setBytes(3, this.hash);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    public void reserve(Date d, String vaccineName) throws SQLException {
        String caregiver = getCaregiver(d);
        if (caregiver.isEmpty()) {
            System.out.println("No caregiver is available");
            return;
        }
        Vaccine vaccine = new Vaccine.VaccineGetter(vaccineName).get();
        if (vaccine == null) {
            System.out.println("Not enough available doses");
            return;
        }
        try {
            vaccine.decreaseAvailableDoses(1);
        } catch (IllegalArgumentException e) {
            System.out.println("Not enough available doses");
            return;
        }
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        String reservation  = "INSERT INTO Reservations VALUES (?, ?, ?, ?, ?)";
        String updateAvailability = "DELETE FROM Availabilities WHERE Time = ? AND Username = ?";
        int resID = newReserveID();
        try {
            PreparedStatement s1 = con.prepareStatement(reservation);
            s1.setInt(1, resID);
            s1.setString(2, vaccineName);
            s1.setString(3, this.username);
            s1.setString(4, caregiver);
            s1.setDate(5, d);
            PreparedStatement s2 = con.prepareStatement(updateAvailability);
            s2.setDate(1, d);
            s2.setString(2, caregiver);
            s1.executeUpdate();
            s2.executeUpdate();
            System.out.println("Appointment ID " + resID + ", Caregiver username " + caregiver);
            s1.close();
        } catch (SQLException e) {
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    // returns "" if no caregiver available for that date
    private String getCaregiver(Date d) throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        String getCaregiver  = "SELECT Username FROM Availabilities WHERE Time = ? ORDER BY Username ASC LIMIT 1";
        try {
            PreparedStatement s1 = con.prepareStatement(getCaregiver);
            s1.setDate(1, d);
            ResultSet res = s1.executeQuery();
            String caregiver;
            if (res.next()) {
                caregiver = res.getString("Username");
            } else {
                caregiver = "";
            }
            res.close();
            s1.close();
            return caregiver;
        } catch (SQLException e) {
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    // helper for reserve
    private int newReserveID() throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        String getCurrID  = "SELECT MAX(ID) FROM Reservations";
        try {
            PreparedStatement s1 = con.prepareStatement(getCurrID);
            ResultSet res = s1.executeQuery();
            res.next();
            int result = res.getInt(1) + 1;
            res.close();
            return result;
        } catch (SQLException e) {
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    public void showAppointments() {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        String getApps = "SELECT ID, Vaccine, Time, Caregiver FROM Reservations WHERE Patient = ? ORDER BY ID ASC";
        try {
            PreparedStatement s1 = con.prepareStatement(getApps);
            s1.setString(1, this.username);
            ResultSet rs = s1.executeQuery();
            boolean hasRes = false;
            while(rs.next()) {
                int ID  = rs.getInt("ID");
                String Vaccine = rs.getString("Vaccine");
                Date d = rs.getDate("Time");
                String caregiver = rs.getString("Caregiver");
                System.out.println(ID + " " + Vaccine + " " + d + " " + caregiver);
                hasRes = true;
            }
            if (!hasRes) {
                System.out.println("No appointments scheduled");
            }
        } catch (SQLException e) {
            System.out.println("Please try again");
        } finally {
            cm.closeConnection();
        }
    }

    public static class PatientBuilder {
        private final String username;
        private final byte[] salt;
        private final byte[] hash;

        public PatientBuilder(String username, byte[] salt, byte[] hash) {
            this.username = username;
            this.salt = salt;
            this.hash = hash;
        }

        public Patient build() {
            return new Patient(this);
        }
    }

    public static class PatientGetter {
        private final String username;
        private final String password;
        private byte[] salt;
        private byte[] hash;

        public PatientGetter(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public Patient get() throws SQLException {
            ConnectionManager cm = new ConnectionManager();
            Connection con = cm.createConnection();

            String getPatient = "SELECT Salt, Hash FROM Patients WHERE Username = ?";
            try {
                PreparedStatement statement = con.prepareStatement(getPatient);
                statement.setString(1, this.username);
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    byte[] salt = resultSet.getBytes("Salt");
                    // we need to call Util.trim() to get rid of the paddings,
                    // try to remove the use of Util.trim() and you'll see :)
                    byte[] hash = Util.trim(resultSet.getBytes("Hash"));
                    // check if the password matches
                    byte[] calculatedHash = Util.generateHash(password, salt);
                    if (!Arrays.equals(hash, calculatedHash)) {
                        return null;
                    } else {
                        this.salt = salt;
                        this.hash = hash;
                        return new Patient(this);
                    }
                }
                return null;
            } catch (SQLException e) {
                throw new SQLException();
            } finally {
                cm.closeConnection();
            }
        }
    }
}
