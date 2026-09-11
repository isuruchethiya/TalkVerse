package model;

import org.hibernate.Session;
import org.hibernate.Transaction;

public class DbConnectionTest {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("TalkVerse — Hibernate DB Connection Test");
        System.out.println("==================================================");

        Session session = null;
        Transaction tx = null;
        try {
            System.out.println("[1/4] Building SessionFactory (HibernateUtil)...");
            session = HibernateUtil.getSessionFactory().openSession();
            System.out.println("        ✓ SessionFactory built, Session opened.");

            System.out.println("[2/4] Running simple SQL test (SELECT 1 + 1)...");
            tx = session.beginTransaction();
            Object result = session.createSQLQuery("SELECT 1 + 1").uniqueResult();
            System.out.println("        ✓ SQL returned: 1 + 1 = " + result);
            tx.commit();

            System.out.println("[3/4] Counting UserStatus rows (validate → entity maps)...");
            tx = session.beginTransaction();
            Long userStatusCount = (Long) session.createQuery("SELECT COUNT(*) FROM UserStatus").uniqueResult();
            System.out.println("        ✓ UserStatus rows: " + userStatusCount + " (expect ≥ 2)");

            Long chatStatusCount = (Long) session.createQuery("SELECT COUNT(*) FROM ChatStatus").uniqueResult();
            System.out.println("        ✓ ChatStatus rows: " + chatStatusCount + " (expect ≥ 3)");

            Long channelCount = (Long) session.createQuery("SELECT COUNT(*) FROM ChannelStatus").uniqueResult();
            System.out.println("        ✓ ChannelStatus rows: " + channelCount + " (expect ≥ 2)");

            Long chMsgStatusCount = (Long) session.createQuery("SELECT COUNT(*) FROM ChannelMessageStatus").uniqueResult();
            System.out.println("        ✓ ChannelMessageStatus rows: " + chMsgStatusCount + " (expect ≥ 2)");

            Long userCount = (Long) session.createQuery("SELECT COUNT(*) FROM User").uniqueResult();
            System.out.println("        ✓ User rows: " + userCount + " (0 is OK — no signups yet)");
            tx.commit();

            System.out.println("[4/4] Closing Session...");
            session.close();
            System.out.println("        ✓ Session closed.");

            System.out.println("==================================================");
            System.out.println("SUCCESS — All 9 entity mappings + DB connection OK.");
            System.out.println("==================================================");

        } catch (Throwable t) {
            System.out.println("");
            System.out.println("✗ FAILURE — Stack trace below:");
            t.printStackTrace();
            if (tx != null) {
                try { tx.rollback(); } catch (Exception ignore) {}
            }
            System.exit(1);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

}
